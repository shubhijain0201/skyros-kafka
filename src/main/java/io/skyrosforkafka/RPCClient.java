package io.skyrosforkafka;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.util.ClientPutRequest;
import io.util.DurabilityKey;
import io.util.DurabilityValue;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RPCClient {

  protected static List <Long> putLatencyTracker;
  protected static List <Long> getLatencyTracker;
  private static final Logger logger = Logger.getLogger(
    RPCClient.class.getName()
  );
  private static final long EXTRA_WAIT = 50;
  protected final List<ManagedChannel> channels = new ArrayList<>();
  protected final List<SkyrosKafkaImplGrpc.SkyrosKafkaImplStub> stubs = new ArrayList<>();
  private static long startPutTime;
  private static long endPutTime;
  private static long startGetTime;
  private static long endGetTime;


  public RPCClient(List<String> serverList, int port) {
    putLatencyTracker = new ArrayList<>();
    getLatencyTracker = new ArrayList<>();
    for (String server : serverList) {
      ManagedChannel channel = ManagedChannelBuilder
        .forAddress(server, port)
        .usePlaintext()
        .build();
      channels.add(channel);
      stubs.add(SkyrosKafkaImplGrpc.newStub(channel));
    }
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
    final int quorum = (int) Math.ceil(stubs.size() / 2.0) + (int) Math.ceil(stubs.size() / 4.0) + 1;
    final AtomicInteger responses = new AtomicInteger(0);
    final AtomicBoolean leaderAcked = new AtomicBoolean(true);
    startPutTime = System.currentTimeMillis();
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
    endPutTime = System.currentTimeMillis();
    putLatencyTracker.add(endPutTime - startPutTime);
    if (responses.get() >= quorum && leaderAcked.get()) kafkaClient.SendNext();
  }

  public void get(
    String topic,
    long numberOfRecords,
    long timeout,
    long offset,
    KafkaClient kafkaClient
  ) {
    logger.info("Trying to get the messages...");

    GetRequest request = GetRequest
            .newBuilder()
            .setTopic(topic)
            .setNumRecords(numberOfRecords)
            .setTimeout(timeout)
            .setOffset(offset)
            .build();

    logger.info("Get Request created!");
    ExecutorService executor = Executors.newFixedThreadPool(stubs.size());
    startGetTime = System.currentTimeMillis();
    for (final SkyrosKafkaImplGrpc.SkyrosKafkaImplStub stub : stubs) {
      logger.info("Async requests sent to servers  ...");
      executor.execute(() -> {
        stub.get(
          request,
          new StreamObserver<GetResponse>() {
            @Override
            public void onNext(GetResponse response) {
              if (!response.getValue().equals("op_not_done")) {}
              endGetTime = System.currentTimeMillis();
              getLatencyTracker.add(endPutTime - startPutTime);
              startGetTime = endGetTime;
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

  public static void main(String[] args) throws Exception {}
}
