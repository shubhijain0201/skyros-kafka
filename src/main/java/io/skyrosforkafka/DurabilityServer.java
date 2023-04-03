package io.skyrosforkafka;

import io.common.CommonReplica;
import io.grpc.stub.StreamObserver;
import io.kafka.ConsumeRecords;
import io.util.*;
import java.io.IOException;
import java.lang.System;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public class DurabilityServer {

  private static final Logger logger = Logger.getLogger(
    DurabilityServer.class.getName()
  );
  private ConcurrentSkipListMap<DurabilityKey, DurabilityValue> durabilityMap;
  private ConcurrentLinkedQueue<MutablePair<DurabilityKey, DurabilityValue>> dataQueue;
  private Properties properties;
  private Map<String, RPCClient> serverMap;
  private KafkaConsumer<String, String> kafkaConsumer;
  private final int myIndex;
  private final String myIP;
  private final int myPort;
  private static Configuration configuration;
  private static String consumerPropertyFileName;
  private final RPCServer rpcServer;

  public DurabilityServer(
    String target,
    List<String> ips,
    int index,
    int port
  ) {
    logger.setLevel(Level.ALL);

    durabilityMap = new ConcurrentSkipListMap<>(durabilityKeyComparator);
    dataQueue = new ConcurrentLinkedQueue<>();
    serverMap = new HashMap<>();

    this.myIndex = index;
    this.myIP = target;
    this.myPort = port;

    for (int i = 0; i < ips.size(); i++) {
      if (ips.get(i).equals(myIP)) {
        continue;
      }
      serverMap.put(ips.get(i), new RPCClient(ips, port));
    }

    rpcServer = new RPCServer(this);
    try {
      rpcServer.start(port);
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
    if (!CommonReplica.isNilext(putRequest.getOpType())) {
      if (!amILeader(putRequest.getTopic())) {
        PutResponse response = PutResponse
          .newBuilder()
          .setValue("op_not_done")
          .setReplicaIndex(myIndex)
          .setRequestId(putRequest.getRequestId())
          .build();

        return response;
      } else {
        // send to producer directly with ack = 0
        PutResponse response = PutResponse
          .newBuilder()
          .setValue("sent to Kafka!")
          .setReplicaIndex(myIndex)
          .setRequestId(putRequest.getRequestId())
          .build();

        return response;
      }
    }

    DurabilityKey durabilityKey = new DurabilityKey(
      putRequest.getClientId(),
      putRequest.getRequestId()
    );
    DurabilityValue durabilityValue = new DurabilityValue(
      putRequest.getMessage(),
      putRequest.getParseKey(),
      putRequest.getKeySeparator(),
      putRequest.getTopic()
    );
    logger.log(Level.INFO, "Message received: " + putRequest.getMessage());
    durabilityMap.put(durabilityKey, durabilityValue);

    if (amILeader(putRequest.getTopic())) {
      dataQueue.add(new MutablePair<>(durabilityKey, durabilityValue));
    }

    PutResponse response = PutResponse
      .newBuilder()
      .setValue("dur-ack")
      .setReplicaIndex(myIndex)
      .setRequestId(putRequest.getRequestId())
      .build();
    return response;
  }

  public GetResponse getDataFromKafka(
    String topic,
    long numRecords,
    long timeout,
    StreamObserver<GetResponse> responseObserver
  ) {
    if (!amILeader(topic)) {
      GetResponse response = GetResponse
        .newBuilder()
        .setValue("op_not_done")
        .build();
      return response;
    }

    logger.log(Level.INFO, "Fetching data from Kafka!");

    if (durabilityMap.size() > 0) {
      long removeIndex = CommonReplica.backgroundReplication(dataQueue);
      // send index to other servers
      sendTrimRequest(removeIndex);
      CommonReplica.clearDurabilityLogTillOffset(removeIndex, durabilityMap); // move to background
    }
    // start consumer to fetch and print records on client
    initConsumer();
    kafkaConsumer.subscribe(Arrays.asList(topic));

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      executor.invokeAll(
        Arrays.asList(
          new ConsumeRecords(kafkaConsumer, numRecords, responseObserver)
        ),
        timeout,
        TimeUnit.SECONDS
      ); // Timeout of 10 seconds.
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    executor.shutdown();
    return null;
  }

  //TODO: Make this parallel
  public void sendTrimRequest(long index) {
    for (Map.Entry<String, RPCClient> entry : serverMap.entrySet()) {
      if (!entry.getValue().equals("10.10.1.3")) continue;
      entry.getValue().trimLog(index);
    }
  }

  public TrimResponse handleTrimRequest(TrimRequest request) {
    // trim the log
    long recordsRemoved = CommonReplica.clearDurabilityLogTillOffset(
      request.getTrimIndex(),
      durabilityMap
    );
    TrimResponse response = TrimResponse
      .newBuilder()
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

  public static void main(String args[]) {
    String target = "0.0.0.0:50051";
    int index = -1;
    String config = null;
    String propertyFile = null;

    ParseServerInput parseServerInput = new ParseServerInput();
    logger.log(Level.INFO, "Argument read " + args[0]);
    CommandLine commandLine = parseServerInput.parseOptions(args);
    if (commandLine.hasOption("c")) {
      config = commandLine.getOptionValue("c");
    } else {
      logger.log(
        Level.SEVERE,
        "Config file not provided, cannot connect to servers. Exit!"
      );
      System.exit(1);
    }

    configuration = new Configuration(config);
    List<String> serverIPs = configuration.getServerIPs();
    int port = configuration.getServerPort();
    if (commandLine.hasOption("s_id")) {
      index = Integer.parseInt(commandLine.getOptionValue("s_id"));
      System.out.println("ID Is " + index);
      target = serverIPs.get(index);
      System.out.println("ID Is " + target);
    } else {
      logger.log(
        Level.SEVERE,
        "ID of this server not provided, cannot start server. Exit!"
      );
      System.exit(1);
    }

    if (commandLine.hasOption("k")) {
      consumerPropertyFileName = commandLine.getOptionValue("k");
    } else {
      logger.log(
        Level.WARNING,
        "No consumer properties file provided. Get requests might fail"
      );
    }

    logger.log(Level.INFO, "Listening to requests...");

    new DurabilityServer(target, serverIPs, index, port);
  }
}
