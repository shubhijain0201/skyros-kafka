package io.skyrosforkafka;

import io.common.CommonReplica;
import io.grpc.stub.StreamObserver;
import io.kafka.ConsumeRecords;
import io.util.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.Callback;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DurabilityServer {
    private final String producerPropertyFileName = "producer_config.properties";
    private KafkaProducer<String, String> kafkaProducer;
    private Properties producerProperties;
                
    private static final Logger logger = Logger.getLogger(DurabilityServer.class.getName());
    private ConcurrentHashMap<DurabilityKey, DurabilityValue> durabilityMap;
    private ConcurrentLinkedQueue<MutablePair<DurabilityKey, DurabilityValue>> dataQueue;
    private Properties properties;
    private Map <String, RPCClient> serverMap;
    private KafkaConsumer<String, String> kafkaConsumer;
    private final int myIndex;
    private final String myIP;
    private static Configuration configuration;
    private static String consumerPropertyFileName;
    private final RPCServer rpcServer;
    
    public DurabilityServer(String target, List <String> ips, int index) {
        logger.setLevel(Level.ALL);

        durabilityMap = new ConcurrentHashMap<>();
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

    public PutResponse putInDurability(PutRequest putRequest) {

        if(putRequest.getRequestId()==0){
            String acks="all";
            switch(putRequest.getOpType())
            {
                case "w_0":acks="0";break;
                case "w_1":acks="1";break;
                case "w_all":acks="all";break;
            }
            properties = new Properties();
            ReplicaUtil.setProducerProperties(properties, producerPropertyFileName, acks);
            kafkaProducer = new KafkaProducer<>(properties);

            if(amILeader(putRequest.getTopic())){
                // periodic task 10 seconds
                ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                long timeout = 10;
                executor.scheduleAtFixedRate(() -> {
                        try{
                            CommonReplica.backgroundReplication(dataQueue, kafkaProducer, durabilityMap);
                        }
                        catch(Exception e)
                        {
                            logger.log(Level.INFO, e.getMessage());
                        }
                }, timeout, timeout, TimeUnit.SECONDS);
            }

        }

        if(!CommonReplica.isNilext(putRequest.getOpType())) {
            if(!amILeader(putRequest.getTopic())) {
                PutResponse response = PutResponse.newBuilder()
                        .setValue("op_not_done")
                        .setReplicaIndex(myIndex)
                        .build();

                return response;
            } else {
                // send to producer directly with ack = 0
                CompletableFuture.runAsync(() -> {
                    String key, value;
                    if (putRequest.getParseKey()) {
                        String[] parts = putRequest.getMessage().split(putRequest.getKeySeparator(), 2);
                        key = parts[0];
                        value = parts[1];
                    } else {
                        key = null;
                        value = putRequest.getMessage();
                    }
                    kafkaProducer.send(new ProducerRecord<>(putRequest.getTopic(), key, value), new Callback() {
                        @Override
                        public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                            if (e != null) {
                                logger.log(Level.SEVERE, "Error while sending message to Kafka", e);
                            } else {
                                logger.log(Level.INFO, "Message sent to Kafka: " + putRequest.getMessage());
                            }
                        }
                    });
                });

                PutResponse response = PutResponse.newBuilder()
                        .setValue("sent to Kafka!")
                        .setReplicaIndex(myIndex)
                        .build();

                return response;
            }
        }

        DurabilityKey durabilityKey = new DurabilityKey(putRequest.getClientId(), putRequest.getRequestId());
        DurabilityValue durabilityValue = new DurabilityValue(putRequest.getMessage(), putRequest.getParseKey(), putRequest.getKeySeparator(), putRequest.getTopic());
        logger.log(Level.INFO, "Message received: " + putRequest.getMessage());
        durabilityMap.put(durabilityKey, durabilityValue);

        if(amILeader(putRequest.getTopic())) {
            dataQueue.add(new MutablePair<>(durabilityKey, durabilityValue));
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
            List<DurabilityKey> trimList = CommonReplica.backgroundReplication(dataQueue);
            // send index to other servers
            sendTrimRequest(trimList);
            for (DurabilityKey key : trimList) {
                CommonReplica.clearDurabilityLogTillOffset(key.getClientId(), key.getRequestId(), durabilityMap); // move to background
            }

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
    public void sendTrimRequest(List<DurabilityKey> trimList) {
        for (Map.Entry<String,RPCClient> entry : serverMap.entrySet()) {
            entry.getValue().trimLog(trimList);
        }
    }

    public boolean handleTrimRequest(TrimRequest request) {
        // trim the log
        boolean isTrimmed = CommonReplica.clearDurabilityLogTillOffset(
                request.getClientId(), request.getRequestId(), durabilityMap);
        return isTrimmed;
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

        new DurabilityServer(target, serverIps, index);

    }
}
