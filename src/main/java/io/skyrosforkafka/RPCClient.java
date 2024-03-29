package io.skyrosforkafka;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.util.ClientPutRequest;
import io.util.DurabilityKey;
import io.util.DurabilityValue;
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
import org.apache.commons.lang3.tuple.MutablePair;

public class RPCClient {

  protected static  List<Long> putLatencyTracker;
  protected  List<Long> getLatencyTracker;
  private static final Logger logger = Logger.getLogger(
    RPCClient.class.getName()
  );
  private static final long EXTRA_WAIT = 50;
  protected final List<ManagedChannel> channels = new ArrayList<>();
  protected final List<SkyrosKafkaImplGrpc.SkyrosKafkaImplStub> stubs = new ArrayList<>();
  private  long finalStartGetTime;
  private  long finalEndGetTime;
  private  long startPutTime;
  private  long endPutTime;
  private  long startGetTime;
  private  long endGetTime;
  private long sumLatencies;
  private ExecutorService executor;

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
    // executor = Executors.newFixedThreadPool(stubs.size()*2);
  }

  public void put(
    ClientPutRequest clientPutRequest,
    KafkaClient kafkaClient,
    int leader
  ) throws InterruptedException {
    // logger.info("Try to write the message = " + clientPutRequest);

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

    // logger.info("Put Request created!" + request.getRequestId());
    final CountDownLatch mainlatch = new CountDownLatch(1);

    executor = Executors.newFixedThreadPool(stubs.size()*2);
    final int quorum = (int) Math.ceil(stubs.size() / 2.0) +
    (int) Math.floor(stubs.size() / 4.0);
    final AtomicInteger responses = new AtomicInteger(0);
    final AtomicBoolean leaderAcked = new AtomicBoolean(true);
    startPutTime = System.currentTimeMillis();
    for (final SkyrosKafkaImplGrpc.SkyrosKafkaImplStub stub : stubs) {
      logger.info("Async put requests sent to servers  ...");
      executor.execute(() -> {
        stub.put(
          request,
          new StreamObserver<PutResponse>() {
            @Override
            public void onNext(PutResponse putResponse) {
              // logger.info(
              //   "Received response from server " +
              //   putResponse.getReplicaIndex() +
              //   " for request " +
              //   putResponse.getRequestId() +
              //   "with value = " +
              //   putResponse.getValue()
              // );
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
              // logger.info("RPC completed");

              int numResponses = responses.incrementAndGet();
              // logger.info(
              //   "The value of responses and  quorum are " +
              //   numResponses +
              //   ", " +
              //   quorum
              // );
              if (numResponses >= quorum && leaderAcked.get()) {
                 endPutTime = System.currentTimeMillis();
                mainlatch.countDown();
                // logger.info(
                //   "In here, Leader acked and the value of responses and  quorum are " +
                //   responses +
                //   ", " +
                //   quorum
                // );
               
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
   
    putLatencyTracker.add(endPutTime - startPutTime);
    if (responses.get() >= quorum && leaderAcked.get())  {executor.shutdown();return;}
    // kafkaClient.SendNext();
  }

  public void get(
    String topic,
    long numberOfRecords,
    long timeout,
    long offset,
    KafkaClient kafkaClient
  ) {
    // logger.info("Trying to get the messages...");
    final AtomicInteger recordsRecieved = new AtomicInteger(0);
    GetRequest request = GetRequest
      .newBuilder()
      .setTopic(topic)
      .setNumRecords(numberOfRecords)
      .setTimeout(timeout)
      .setOffset(offset)
      .build();

    sumLatencies = 0;

    // logger.info("Get Request created!");
    ExecutorService executor = Executors.newFixedThreadPool(stubs.size()*2);
    final CountDownLatch mainlatch = new CountDownLatch(1);

    startGetTime = System.currentTimeMillis();
    finalStartGetTime = startGetTime;
    for (final SkyrosKafkaImplGrpc.SkyrosKafkaImplStub stub : stubs) {
      logger.info("Async get requests sent to servers  ...");
      executor.execute(() -> {
        stub.get(
          request,
          new StreamObserver<GetResponse>() {
            @Override
            public void onNext(GetResponse response) {
              if (!response.getValue().equals("op_not_done")) {
              endGetTime = System.currentTimeMillis();
              getLatencyTracker.add(endGetTime - startGetTime);
              sumLatencies = sumLatencies + (endGetTime - startGetTime);
              // logger.log(Level.INFO, "Time taken for get in on next:" + (finalEndGetTime- finalStartGetTime));
              startGetTime = endGetTime;
                long numRecordsRecieved = recordsRecieved.incrementAndGet();
               if(numRecordsRecieved >= numberOfRecords){    
                finalEndGetTime = endGetTime;
                // logger.log(Level.INFO, "Time taken for get in on next:" + (finalEndGetTime- finalStartGetTime));
                mainlatch.countDown();
              }
              // logger.log(Level.INFO, "Received data: {0}", response.getValue());
              
              // System.out.println("Recrds received =  " + numRecordsRecieved);
               
              
              }
            }

            @Override
            public void onError(Throwable t) {
              logger.log(Level.WARNING, "RPC failed: {0}", t.getMessage());
            }

            @Override
            public void onCompleted() {
            
                // logger.log(Level.INFO, "Time taken for get in on completed:" + (finalEndGetTime- finalStartGetTime));
                // logger.log(Level.INFO, "Sum of latencies {0}", sumLatencies);
               

               
              
              
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
    long getsum = 0;
      // for (Long value : getLatencyTracker) {
      //   getsum += value;
      // }
      // System.out.println("Get latency values = "+ getsum);
    logger.log(Level.INFO, "Sum of latencies after latch {0}", sumLatencies);

    return;
  }

  public static void main(String[] args) throws Exception {}
}
