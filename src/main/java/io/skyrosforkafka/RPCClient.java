package io.skyrosforkafka;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.util.ClientPutRequest;
import io.util.Configuration;
import io.util.DurabilityKey;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.checkerframework.checker.units.qual.C;

public class RPCClient {

  private static final Logger logger = Logger.getLogger(
    RPCClient.class.getName()
  );

  private static final long EXTRA_WAIT = 50;
  // private final SkyrosKafkaImplGrpc.SkyrosKafkaImplBlockingStub blockingStub;
  protected final List<ManagedChannel> channels = new ArrayList<>();
  protected final List<SkyrosKafkaImplGrpc.SkyrosKafkaImplStub> stubs = new ArrayList<>();
  private final SkyrosKafkaImplGrpc.SkyrosKafkaImplStub trimAsyncStub;

  public RPCClient(List<String> serverList, int port) {
    for (String server : serverList) {
      ManagedChannel channel = ManagedChannelBuilder
        .forAddress(server, port)
        .usePlaintext()
        .build();
      channels.add(channel);
      stubs.add(SkyrosKafkaImplGrpc.newStub(channel));
    }

    ManagedChannel channel = ManagedChannelBuilder
      .forAddress("10.10.1.3", port)
      .usePlaintext()
      .build();
    // blockingStub = SkyrosKafkaImplGrpc.newBlockingStub(channel);
    trimAsyncStub = SkyrosKafkaImplGrpc.newStub(channel);
  }

  public void put(
    ClientPutRequest clientPutRequest,
    KafkaClient kafkaClient,
    int leader
  ) throws InterruptedException {
    logger.info("Try to write the message = " + clientPutRequest);

    PutRequest request = PutRequest
      .newBuilder()
      .setMessage(clientPutRequest.getMessage())
      .setClientId(clientPutRequest.getClientId())
      .setRequestId(clientPutRequest.getRequestId())
      .setParseKey(clientPutRequest.isParseKey())
      .setKeySeparator(clientPutRequest.getKeySeparator())
      .setOpType(clientPutRequest.getOpType())
      .setTopic(clientPutRequest.getTopic())
      .build();

    logger.info("Put Request created!" + request.getRequestId());
    final CountDownLatch mainlatch = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(stubs.size());
    final int quorum = (int) Math.ceil(stubs.size() / 2.0);
    final AtomicInteger responses = new AtomicInteger(0);
    final AtomicBoolean leaderAcked = new AtomicBoolean(true);
    for (final SkyrosKafkaImplGrpc.SkyrosKafkaImplStub stub : stubs) {
      logger.info("Async requests sent to servers  ...");
      executor.execute(() -> {
        stub.put(
          request,
          new StreamObserver<PutResponse>() {
            @Override
            public void onNext(PutResponse putResponse) {
              logger.info(
                "Received response from server " +
                putResponse.getReplicaIndex() +
                " for request " +
                putResponse.getRequestId() +
                "with value = " +
                putResponse.getValue()
              );
              if (putResponse.getReplicaIndex() == leader) leaderAcked.set(
                true
              );
            }

            @Override
            public void onError(Throwable t) {
              logger.log(Level.WARNING, "RPC failed: {0}", t.getMessage());
            }

            @Override
            public void onCompleted() {
              logger.info("RPC completed");

              int numResponses = responses.incrementAndGet();
              logger.info(
                "The value of responses and  quorum are " +
                numResponses +
                ", " +
                quorum
              );
              if (numResponses >= quorum && leaderAcked.get()) {
                mainlatch.countDown();
                logger.info(
                  "In here, Leader acked and the value of responses and  quorum are " +
                  responses +
                  ", " +
                  quorum
                );
                executor.shutdown();
                try {
                  executor.awaitTermination(40, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  logger.log(
                    Level.WARNING,
                    "Interrupted while waiting for executor to terminate",
                    e
                  );
                }
              }
            }
          }
        );
      });
    }
    try {
      mainlatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.log(
        Level.WARNING,
        "Interrupted while waiting for executor to terminate",
        e
      );
    }
    if (responses.get() >= quorum && leaderAcked.get()) kafkaClient.SendNext();
  }

  public void get(
    String topic,
    long numberOfRecords,
    long timeout,
    KafkaClient kafkaClient
  ) {
    logger.info("Trying to get the messages...");

    GetRequest request = GetRequest
      .newBuilder()
      .setTopic(topic)
      .setNumRecords(numberOfRecords)
      .setTimeout(timeout)
      .build();

    logger.info("Get Request created!");
    ExecutorService executor = Executors.newFixedThreadPool(stubs.size());
    for (final SkyrosKafkaImplGrpc.SkyrosKafkaImplStub stub : stubs) {
      logger.info("Async requests sent to servers  ...");
      executor.execute(() -> {
        stub.get(
          request,
          new StreamObserver<GetResponse>() {
            @Override
            public void onNext(GetResponse response) {
              if (!response.getValue().equals("op_not_done")) {}
              logger.log(Level.INFO, "Received data: {0}", response.getValue());
            }

            @Override
            public void onError(Throwable t) {
              logger.log(Level.WARNING, "RPC failed: {0}", t.getMessage());
            }

            @Override
            public void onCompleted() {}
          }
        );
      });
    }
  }

  // public void trimLog(List<DurabilityKey> trimList) {
  //   final CountDownLatch finishLatch = new CountDownLatch(1);

  //   StreamObserver<TrimResponse> responseObserver = new StreamObserver<TrimResponse>() {
  //     @Override
  //     public void onNext(TrimResponse trimResponse) {
  //       logger.log(
  //         Level.INFO,
  //         "Number of entries removed from log {0}",
  //         trimResponse.getTrimCount()
  //       );
  //     }

  //     @Override
  //     public void onError(Throwable throwable) {
  //       Status status = Status.fromThrowable(throwable);
  //       logger.log(Level.WARNING, "Trim log failed: {0}", status);
  //       finishLatch.countDown();
  //     }

  //     @Override
  //     public void onCompleted() {
  //       logger.log(Level.INFO, "Finished trimming");
  //       finishLatch.countDown();
  //     }
  //   };

  //   StreamObserver<TrimRequest> requestObserver = trimAsyncStub.trimLog(
  //     responseObserver
  //   );
  //   try {
  //     for (DurabilityKey durabilityKey : trimList) {
  //       requestObserver.onNext(
  //         TrimRequest
  //           .newBuilder()
  //           .setClientId(durabilityKey.getClientId())
  //           .setRequestId(durabilityKey.getRequestId())
  //           .build()
  //       );
  //       Thread.sleep(1000);
  //       if (finishLatch.getCount() == 0) {
  //         return;
  //       }
  //     }
  //   } catch (StatusRuntimeException e) {
  //     requestObserver.onError(e);
  //     logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
  //   } catch (InterruptedException e) {
  //     e.printStackTrace();
  //   }
  //   requestObserver.onCompleted();
  //   try {
  //     finishLatch.await(5, TimeUnit.MINUTES);
  //   } catch (InterruptedException e) {
  //     throw new RuntimeException(e);
  //   }
  // }

  public static void main(String[] args) throws Exception {}
}
