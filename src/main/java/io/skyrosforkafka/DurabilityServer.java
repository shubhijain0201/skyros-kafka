package io.skyrosforkafka;

import io.common.CommonReplica;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.kafka.ConsumeRecords;
import io.util.*;
import io.util.ClientPutRequest;
import io.util.Configuration;
import io.util.DurabilityKey;
import java.io.IOException;
import java.lang.System;
import java.util.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.checkerframework.checker.units.qual.C;

public class DurabilityServer {

  private final String producerPropertyFileName = "producer_config.properties";
  private KafkaProducer<String, String> kafkaProducer;
  private Properties producerProperties;

  private static final Logger logger = Logger.getLogger(
    DurabilityServer.class.getName()
  );
  private ConcurrentHashMap<DurabilityKey, DurabilityValue> durabilityMap;
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
  private final RPCClient durabilityClient;
  private final ScheduledExecutorService executor;
  private final ScheduledExecutorService trimExecutor;

  private long timeout;
  // private List<DurabilityKey> trimList;
  private ConcurrentHashMap<Integer, List<DurabilityKey>> trimListMap;
  private boolean istrimListRunning;
  private boolean isclearLogRunning;
  private AtomicInteger backgroundRuns;
  private AtomicInteger trimRuns;
  private AtomicInteger clearLogRuns;

  public DurabilityServer(
    String target,
    List<String> ips,
    int index,
    int port
  ) {
    logger.setLevel(Level.ALL);
    logger.info("My server IP is = " + target);

    durabilityMap = new ConcurrentHashMap<>();
    dataQueue = new ConcurrentLinkedQueue<>();
    serverMap = new HashMap<>();
    trimListMap = new ConcurrentHashMap<>();
    backgroundRuns = new AtomicInteger(0);
    trimRuns = new AtomicInteger(0);
    clearLogRuns = new AtomicInteger(0);

    this.myIndex = index;
    this.myIP = target;
    this.myPort = port;
    this.durabilityClient = new RPCClient(ips, port);

    rpcServer = new RPCServer(this);
    try {
      rpcServer.start(port);

      executor = Executors.newSingleThreadScheduledExecutor();
      timeout = 3;
      executor.scheduleAtFixedRate(
        () -> {
          try {
            if (dataQueue.size() > 0 && amILeader("topic")) { //change topic later
              logger.log(
                Level.INFO,
                "Before Durability Map size " +
                durabilityMap.size() +
                "\t Data Queue size " +
                dataQueue.size()
              );
              List<DurabilityKey> trimList = CommonReplica.backgroundReplication(
                dataQueue,
                kafkaProducer
              );
              int producerCalls = backgroundRuns.incrementAndGet();
              trimListMap.put(producerCalls, trimList);
              logger.log(
                Level.INFO,
                "Background replication calls: " + producerCalls
              );
            }
          } catch (Exception e) {
            logger.log(Level.INFO, "Background replication failed");
            logger.log(Level.INFO, e.getMessage());
          }
        },
        0,
        timeout,
        TimeUnit.SECONDS
      );

      trimExecutor = Executors.newSingleThreadScheduledExecutor();

      trimExecutor.scheduleAtFixedRate(
        () -> {
          try {
            if (trimRuns.get() < backgroundRuns.get() && amILeader("topic")) {
              logger.log(Level.INFO, "Trim calls " + trimRuns.get());
              // logger.log(Level.INFO, "Trim task started" + trimList.size());
              int trimCalls = trimRuns.incrementAndGet();
              sendTrimRequest(trimListMap.get(trimCalls));
            }
          } catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.INFO, "Trimlogs");
            logger.log(Level.INFO, e.getMessage());
          }
        },
        0,
        timeout,
        TimeUnit.SECONDS
      );

      rpcServer.blockUntilShutdown();

      executor.shutdown();
      trimExecutor.shutdown();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public PutResponse putInDurability(PutRequest putRequest) {
    logger.log(Level.INFO, "In durability put : " + putRequest.getRequestId());

    String acks = "all";
    switch (putRequest.getOpType()) {
      case "w_0":
        acks = "0";
        break;
      case "w_1":
        acks = "1";
        break;
      case "w_all":
        acks = "all";
        break;
    }
    properties = new Properties();
    ReplicaUtil.setProducerProperties(
      properties,
      producerPropertyFileName,
      acks
    );
    kafkaProducer = new KafkaProducer<>(properties);

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
        CompletableFuture.runAsync(() -> {
          // complete background replication before sending new messages
          if (dataQueue.size() > 0) {
            List<DurabilityKey> trimList = CommonReplica.backgroundReplication(
              dataQueue,
              kafkaProducer
            );
            int producerCalls = backgroundRuns.incrementAndGet();
            trimListMap.put(producerCalls, trimList);
            logger.log(
              Level.INFO,
              "Background replication calls: " + producerCalls
            );
          }

          String key, value;
          if (putRequest.getParseKey()) {
            String[] parts = putRequest
              .getMessage()
              .split(putRequest.getKeySeparator(), 2);
            key = parts[0];
            value = parts[1];
          } else {
            key = null;
            value = putRequest.getMessage();
          }
          kafkaProducer.send(
            new ProducerRecord<>(putRequest.getTopic(), key, value),
            new Callback() {
              @Override
              public void onCompletion(
                RecordMetadata recordMetadata,
                Exception e
              ) {
                if (e != null) {
                  logger.log(
                    Level.SEVERE,
                    "Error while sending message to Kafka",
                    e
                  );
                } else {
                  logger.log(
                    Level.INFO,
                    "Message sent to Kafka: " + putRequest.getMessage()
                  );
                }
              }
            }
          );
        });

        PutResponse response = PutResponse
          .newBuilder()
          .setValue("sent to Kafka!")
          .setReplicaIndex(myIndex)
          .setRequestId(putRequest.getRequestId())
          .build();

        return response;
      }
    }

    final DurabilityKey durabilityKey = new DurabilityKey(
      putRequest.getClientId(),
      putRequest.getRequestId()
    );
    final DurabilityValue durabilityValue = new DurabilityValue(
      putRequest.getMessage(),
      putRequest.getParseKey(),
      putRequest.getKeySeparator(),
      putRequest.getTopic()
    );
    logger.log(Level.INFO, "Message received: " + putRequest.getMessage());
    durabilityMap.put(durabilityKey, durabilityValue);
    logger.log(Level.INFO, "Durability size : " + durabilityMap.size());

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

    CompletableFuture.runAsync(() -> {
      // complete background replication before sending new messages
      if (dataQueue.size() > 0) {
        List<DurabilityKey> trimList = CommonReplica.backgroundReplication(
          dataQueue,
          kafkaProducer
        );
        int producerCalls = backgroundRuns.incrementAndGet();
        trimListMap.put(producerCalls, trimList);
        logger.log(
          Level.INFO,
          "Background replication calls: " + producerCalls
        );
      }
    });
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

  // private void sendTrimRequest(List<DurabilityKey> trimList) {
  //   ExecutorService executor = Executors.newFixedThreadPool(5);
  //   final CountDownLatch finishLatch = new CountDownLatch(
  //     durabilityClient.stubs.size() / 2
  //   );
  //   for (final SkyrosKafkaImplGrpc.SkyrosKafkaImplStub stub : durabilityClient.stubs) {
  //     StreamObserver<TrimResponse> responseObserver = new StreamObserver<TrimResponse>() {
  //       @Override
  //       public void onNext(TrimResponse trimResponse) {
  //         logger.log(
  //           Level.INFO,
  //           "Number of entries removed from log {0}",
  //           trimResponse.getTrimCount()
  //         );
  //       }

  //       @Override
  //       public void onError(Throwable throwable) {
  //         Status status = Status.fromThrowable(throwable);
  //         logger.log(Level.WARNING, "Trim log failed: {0}", status);
  //         finishLatch.countDown();
  //       }

  //       @Override
  //       public void onCompleted() {
  //         logger.log(Level.INFO, "Finished trimming");
  //         finishLatch.countDown();
  //       }
  //     };

  //     StreamObserver<TrimRequest> requestObserver = stub.trimLog(
  //       responseObserver
  //     );
  //     try {
  //       for (DurabilityKey durabilityKey : trimList) {
  //         requestObserver.onNext(
  //           TrimRequest
  //             .newBuilder()
  //             .setClientId(durabilityKey.getClientId())
  //             .setRequestId(durabilityKey.getRequestId())
  //             .build()
  //         );
  //         Thread.sleep(1000);
  //         if (finishLatch.getCount() == 0) {
  //           return;
  //         }
  //       }
  //     } catch (StatusRuntimeException e) {
  //       requestObserver.onError(e);
  //       logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
  //     } catch (InterruptedException e) {
  //       e.printStackTrace();
  //     }
  //     requestObserver.onCompleted();
  //     try {
  //       finishLatch.await(5, TimeUnit.MINUTES);
  //     } catch (InterruptedException e) {
  //       throw new RuntimeException(e);
  //     }
  //   }
  // }
  private void sendTrimRequest(List<DurabilityKey> trimList) {
    ExecutorService executor = Executors.newFixedThreadPool(5);
    final AtomicInteger numNodesResponded = new AtomicInteger(0);
    int numNodesExpected = durabilityClient.stubs.size();
    final CountDownLatch mainlatch = new CountDownLatch(numNodesExpected);
    for (final SkyrosKafkaImplGrpc.SkyrosKafkaImplStub stub : durabilityClient.stubs) {
      executor.execute(() -> {
        StreamObserver<TrimRequest> requestObserver = stub.trimLog(
          new StreamObserver<TrimResponse>() {
            @Override
            public void onNext(TrimResponse trimResponse) {
              System.out.println(
                "Number of entries removed from log: " +
                trimResponse.getTrimCount()
              );
              logger.log(Level.INFO, "here i ner");
              logger.log(
                Level.INFO,
                "Number of entries removed from log {0}",
                trimResponse.getTrimCount()
              );
            }

            @Override
            public void onError(Throwable throwable) {
              Status status = Status.fromThrowable(throwable);
              logger.log(Level.WARNING, "Trim log failed: {0}", status);
              mainlatch.countDown();
            }

            @Override
            public void onCompleted() {
              logger.log(Level.INFO, "Finished trimming");
              int numResponses = numNodesResponded.incrementAndGet();
              logger.info(
                "The value of responses and  numNodesExpected are " +
                numResponses +
                ", " +
                numNodesExpected
              );
              mainlatch.countDown();
            }
          }
        );
        for (DurabilityKey durabilityKey : trimList) {
          requestObserver.onNext(
            TrimRequest
              .newBuilder()
              .setClientId(durabilityKey.getClientId())
              .setRequestId(durabilityKey.getRequestId())
              .build()
          );
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        logger.info("Requests done");
        requestObserver.onCompleted();
      });
    }
    executor.shutdown();
  }

  // }

  // private void sendTrimRequest(List<DurabilityKey> trimList) {
  //   ExecutorService executor = Executors.newFixedThreadPool(5);

  //   // Send the TrimRequests to all nodes in parallel
  //   for (final SkyrosKafkaImplGrpc.SkyrosKafkaImplStub stub : durabilityClient.stubs) {
  //     for (DurabilityKey durabilityKey : trimList) {
  //       // StreamObserver<TrimRequest> requestObserver = stub.trimLog(
  //       //   responseObserver
  //       // );
  //       executor.execute(() -> {
  //         StreamObserver<TrimResponse> responseObserver = new StreamObserver<TrimResponse>() {
  //           int numNodesResponded = 0;
  //           int numNodesExpected =
  //             durabilityClient.stubs.size() * trimList.size();

  //           @Override
  //           public void onNext(TrimResponse trimResponse) {
  //             System.out.println(
  //               "Number of entries removed from log: " +
  //               trimResponse.getTrimCount()
  //             );
  //             logger.log(Level.INFO, "here i ner");
  //             logger.log(
  //               Level.INFO,
  //               "Number of entries removed from log {0}",
  //               trimResponse.getTrimCount()
  //             );
  //             numNodesResponded++;
  //             if (numNodesResponded == numNodesExpected) {
  //               logger.log(
  //                 Level.INFO,
  //                 "All nodes have responded. Finished trimming."
  //               );
  //             }
  //           }

  //           @Override
  //           public void onError(Throwable throwable) {
  //             Status status = Status.fromThrowable(throwable);
  //             logger.log(Level.WARNING, "Trim log failed: {0}", status);
  //           }

  //           @Override
  //           public void onCompleted() {
  //             logger.log(Level.INFO, "Finished trimming");
  //           }
  //         };
  //         StreamObserver<TrimRequest> requestObserver = stub.trimLog(
  //           responseObserver
  //         );
  //         requestObserver.onNext(
  //           TrimRequest
  //             .newBuilder()
  //             .setClientId(durabilityKey.getClientId())
  //             .setRequestId(durabilityKey.getRequestId())
  //             .build()
  //         );
  //         try {
  //           Thread.sleep(1000);
  //         } catch (InterruptedException e) {
  //           e.printStackTrace();
  //         }
  //         requestObserver.onCompleted();
  //       });
  //     }
  //   }

  //   executor.shutdown();
  // }

  //     StreamObserver<TrimResponse> responseObserver = new StreamObserver<TrimResponse>() {
  //       @Override
  //       public void onNext(TrimResponse trimResponse) {
  //         logger.log(
  //           Level.INFO,
  //           "Number of entries removed from log {0}",
  //           trimResponse.getTrimCount()
  //         );
  //       }

  //       @Override
  //       public void onError(Throwable throwable) {
  //         Status status = Status.fromThrowable(throwable);
  //         logger.log(Level.WARNING, "Trim log failed: {0}", status);
  //         // latch.countDown();
  //       }

  //       @Override
  //       public void onCompleted() {
  //         logger.log(Level.INFO, "Finished trimming");
  //         // latch.countDown();
  //       }
  //     };

  //     for (DurabilityKey durabilityKey : trimList) {
  //       StreamObserver<TrimRequest> requestObserver = stub.trimLog(
  //         responseObserver
  //       );
  //       executor.execute(() -> {
  //         requestObserver.onNext(
  //           TrimRequest
  //             .newBuilder()
  //             .setClientId(durabilityKey.getClientId())
  //             .setRequestId(durabilityKey.getRequestId())
  //             .build()
  //         );
  //         try {
  //           Thread.sleep(1000);
  //         } catch (InterruptedException e) {
  //           e.printStackTrace();
  //         }
  //         requestObserver.onCompleted(); // Adding the onCompleted() call inside the loop
  //       });
  //     }
  //   }

  //   executor.shutdown();
  // }

  public boolean handleTrimRequest(TrimRequest request) {
    // trim the log
    logger.log(
      Level.INFO,
      "Before Durability Map size " + durabilityMap.size()
    );
    boolean isTrimmed = CommonReplica.clearDurabilityLogTillOffset(
      request.getClientId(),
      request.getRequestId(),
      durabilityMap
    );
    logger.log(
      Level.INFO,
      "After Durability Map size " +
      durabilityMap.size() +
      "\t Data Queue size " +
      dataQueue.size()
    );
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
