package io.skyrosforkafka;

import io.common.CommonReplica;
import io.grpc.stub.StreamObserver;
import io.kafka.ConsumeRecords;
import io.util.*;
import org.apache.commons.cli.CommandLine;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.concurrent.*;

import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public class DurabilityServer {

    private static final String TOPIC_NAME = "aks-topic"; //read from user
    private static final String BROKER_ADDRESS = "localhost:9092"; // read from config file

    private static final Logger logger = Logger.getLogger(DurabilityServer.class.getName());
    private ConcurrentSkipListMap<DurabilityKey, DurabilityValue> durabilityMap;
    private static ConcurrentLinkedQueue<DurabilityValue> dataQueue; //static 
    private Properties properties;
    private Map <String, RPCClient> serverMap;
    private KafkaConsumer<String, String> kafkaConsumer;
    private final int myIndex;
    private final String myIP;
    private static Configuration configuration;
    private static String consumerPropertyFileName;
    private final RPCServer rpcServer;

    private Properties props = new Properties();
    
    private static KafkaProducer<String, String> producer;
        public DurabilityServer(String target, List <String> ips, int index) {
        logger.setLevel(Level.ALL);

        durabilityMap = new ConcurrentSkipListMap<>(durabilityKeyComparator);
        dataQueue = new ConcurrentLinkedQueue<>();
        serverMap = new HashMap<>();

        this.myIndex = index;
        this.myIP = target;

        for (int i = 0; i < ips.size(); i++) {
            if (ips.get(i).equals(myIP)) {
                continue;
            }
            serverMap.put(ips.get(i), new RPCClient(ips.get(i)));
        }


        dataQueue = new ConcurrentLinkedQueue<>();
        props.put("bootstrap.servers", BROKER_ADDRESS);
        props.put("acks", "all");
        props.put("retries", 0);
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producer = new KafkaProducer<>(props);

        rpcServer = new RPCServer(this);
        try {
            rpcServer.start(Integer.parseInt(myIP.split(":")[1]));
            rpcServer.blockUntilShutdown();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Comparator<DurabilityKey> durabilityKeyComparator = (key1, key2) -> {
        Integer index1 = key1.getIndex();
        Integer index2 = key2.getIndex();
        return index1.compareTo(index2);
    };

    public PutResponse putInDurability(PutRequest putRequest) {

        if(!CommonReplica.isNilext(putRequest.getOpType())) {
            if(!amILeader(putRequest.getTopic())) {
                PutResponse response = PutResponse.newBuilder()
                        .setValue("op_not_done")
                        .setReplicaIndex(myIndex)
                        .build();

                return response;
            } else {
                // send to producer directly with ack = 0
                PutResponse response = PutResponse.newBuilder()
                        .setValue("sent to Kafka!")
                        .setReplicaIndex(myIndex)
                        .build();

                return response;
            }
        }

        DurabilityKey durabilityKey = new DurabilityKey(putRequest.getClientId(), putRequest.getRequestId());
        DurabilityValue durabilityValue = new DurabilityValue(putRequest.getMessage(), putRequest.getParseKey(),
                putRequest.getKeySeparator(), putRequest.getTopic());
        logger.log(Level.INFO, "Message received: " + putRequest.getMessage());
        durabilityMap.put(durabilityKey, durabilityValue);

        if(amILeader(putRequest.getTopic())) {
            logger.log(Level.INFO, "data added");
            dataQueue.add(durabilityValue);
        }

        PutResponse response = PutResponse.newBuilder()
                                          .setValue("dur-ack")
                                          .setReplicaIndex(myIndex)
                                          .build();
        return response;
    }

    public GetResponse getDataFromKafka(String topic, long numRecords, long timeout,
                                        StreamObserver<GetResponse> responseObserver) {
        if(!amILeader(topic)) {
            GetResponse response = GetResponse.newBuilder()
                    .setValue("op_not_done")
                    .build();
            return response;
        }

        logger.log(Level.INFO, "Fetching data from Kafka!");

        if (durabilityMap.size() > 0) {
            long removeIndex = CommonReplica.backgroundReplication(dataQueue);
            CommonReplica.clearDurabilityLogTillOffset(removeIndex, durabilityMap); // move to background
            // send index to other servers
            sendTrimRequest(removeIndex);
        }
        // start consumer to fetch and print records on client
        initConsumer();
        kafkaConsumer.subscribe(Arrays.asList(topic));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.invokeAll(Arrays.asList(new ConsumeRecords(kafkaConsumer, numRecords, responseObserver)),
                    timeout, TimeUnit.SECONDS); // Timeout of 10 seconds.
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        executor.shutdown();
        return null;
    }
    public void sendTrimRequest(long index) {
        for (Map.Entry<String,RPCClient> entry : serverMap.entrySet()) {
            entry.getValue().trimLog(index);
        }
    }

    public TrimResponse handleTrimRequest(TrimRequest request) {
        // trim the log
        long recordsRemoved =
                CommonReplica.clearDurabilityLogTillOffset(request.getTrimIndex(), durabilityMap);
        TrimResponse response = TrimResponse.newBuilder()
                                .setTrimCount(recordsRemoved)
                                .build();
        return response;
    }

    private void initConsumer() {
        properties = new Properties();
        ReplicaUtil.setConsumerProperties(properties, consumerPropertyFileName);
        kafkaConsumer = new KafkaConsumer<>(properties);
    }
    private boolean amILeader(String topic) {
        // check from CLI
        return myIndex == configuration.getLeader();
    }



    public static void main(String args[]){

        String target = "0.0.0.0:50051";
        int index = -1;
        String config = null;
        String propertyFile = null;

        ParseServerInput parseServerInput = new ParseServerInput();
        logger.log(Level.INFO, "Argument read " + args[0]);
        CommandLine commandLine = parseServerInput.parseOptions(args);

        if(commandLine.hasOption("c")) {
            config = commandLine.getOptionValue("c");
        } else {
            logger.log(Level.SEVERE, "Config file not provided, cannot connect to servers. Exit!");
            System.exit(1);
        }

        configuration = new Configuration(config);
        List<String> serverIps = configuration.getServerIPs();

        if(commandLine.hasOption("t")) {
            target = commandLine.getOptionValue("t");
        } else {
            logger.log(Level.SEVERE, "IP of this server not provided, cannot start server. Exit!");
            System.exit(1);
        }

        if(commandLine.hasOption("s_id")) {
            index = Integer.parseInt(commandLine.getOptionValue("s_id"));
        } else {
            logger.log(Level.SEVERE, "ID of this server not provided, cannot start server. Exit!");
            System.exit(1);
        }

        if(commandLine.hasOption("k")) {
            consumerPropertyFileName = commandLine.getOptionValue("k");
        } else {
            logger.log(Level.WARNING, "No consumer properties file provided. Get requests might fail");
        }

        logger.log(Level.INFO, "Listening to requests...");

        // periodic task
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        // Set the timeout to 5 seconds
        long timeout = 15;

        // Schedule the task to run after the timeout
        executor.scheduleAtFixedRate(() -> {
                try{
                    //ConcurrentLinkedQueue<DurabilityValue> copyQueue = new ConcurrentLinkedQueue<DurabilityValue>();
                    //copyQueue.addAll(dataQueue);
                    //dataQueue.clear();
                    //CommonReplica.backgroundReplication(copyQueue);
                    DurabilityValue tempValue;
                    while (!dataQueue.isEmpty()) {
                        tempValue = dataQueue.poll();
                        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_NAME, tempValue.message);
                        Future<RecordMetadata> future = producer.send(record);
                        // wait for the acknowledgement to be received
                        RecordMetadata metadata = future.get();
                        logger.log(Level.INFO, "Message sent to partition " + metadata.partition() + " with offset " + metadata.offset());
                    }
                }
                catch(Exception e)
                {
                    logger.log(Level.INFO, e.getMessage());
                }
        }, timeout, timeout, TimeUnit.SECONDS);

        // periodic task
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        // Set the timeout to 5 seconds
        long timeout = 15;

        // Schedule the task to run after the timeout
        executor.scheduleAtFixedRate(() -> {
                try{
                    //ConcurrentLinkedQueue<DurabilityValue> copyQueue = new ConcurrentLinkedQueue<DurabilityValue>();
                    //copyQueue.addAll(dataQueue);
                    //dataQueue.clear();
                    //CommonReplica.backgroundReplication(copyQueue);
                    DurabilityValue tempValue;
                    while (!dataQueue.isEmpty()) {
                        tempValue = dataQueue.poll();
                        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_NAME, tempValue.message);
                        Future<RecordMetadata> future = producer.send(record);
                        // wait for the acknowledgement to be received
                        RecordMetadata metadata = future.get();
                        logger.log(Level.INFO, "Message sent to partition " + metadata.partition() + " with offset " + metadata.offset());
                    }
                }
                catch(Exception e)
                {
                    logger.log(Level.INFO, e.getMessage());
                }
        }, timeout, timeout, TimeUnit.SECONDS);

        new DurabilityServer(target, serverIps, index);

    }
}
