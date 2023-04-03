package io.skyrosforkafka;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.util.ClientPutRequest;
import io.util.DurabilityKey;
import org.checkerframework.checker.units.qual.C;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RPCClient {
    private static final Logger logger = Logger.getLogger(RPCClient.class.getName());

    private static final long EXTRA_WAIT = 50;

    private final SkyrosKafkaImplGrpc.SkyrosKafkaImplBlockingStub blockingStub;

    private final SkyrosKafkaImplGrpc.SkyrosKafkaImplStub trimAsyncStub;

    public RPCClient (String serverIP) {
        ManagedChannel channel = null;
        //try{
            channel = Grpc.newChannelBuilder(serverIP, InsecureChannelCredentials.create())
                        .build();
            blockingStub = SkyrosKafkaImplGrpc.newBlockingStub(channel);
            trimAsyncStub = SkyrosKafkaImplGrpc.newStub(channel);
         //}
//         finally {
//             // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
//             // resources the channel should be shut down when it will no longer be used. If it may be used
//             // again leave it running.
//             try {
//                 channel.shutdownNow().awaitTermination(500000, TimeUnit.SECONDS);
//             } catch (InterruptedException e) {
//                 throw new RuntimeException(e);
//             }
//         }

    }

    public void put(ClientPutRequest clientPutRequest, KafkaClient kafkaClient) {
        logger.info("Try to write the message = " + clientPutRequest);

        PutRequest request = PutRequest.newBuilder()
                .setMessage(clientPutRequest.getMessage())
                .setClientId(clientPutRequest.getClientId())
                .setRequestId(clientPutRequest.getRequestId())
                .setParseKey(clientPutRequest.isParseKey())
                .setKeySeparator(clientPutRequest.getKeySeparator())
                .setOpType(clientPutRequest.getOpType())
                .setTopic(clientPutRequest.getTopic())
                .build();

        logger.info("Request created!");

        PutResponse response;;
        try {
            response = blockingStub.put(request);
            kafkaClient.handlePutReply(response);

        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Response from server: " + response.getValue());

    }

    public void get(String topic, long numberOfRecords, long timeout, KafkaClient kafkaClient) {
        logger.info("Try to get the messages");

        GetRequest request = GetRequest.newBuilder()
                .setTopic(topic)
                .setNumRecords(numberOfRecords)
                .setTimeout(timeout)
                .build();

        logger.info("Request created!");

        Iterator<GetResponse> response;
        try {
            response = blockingStub.withDeadlineAfter(timeout + EXTRA_WAIT, TimeUnit.SECONDS).get(request);
            kafkaClient.handleGetReply(response);

        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        //logger.info("Response from server: " + response.getValue());

    }

    public void trimLog(List<DurabilityKey> trimList) {

        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<TrimResponse> responseObserver = new StreamObserver<TrimResponse>() {
            @Override
            public void onNext(TrimResponse trimResponse) {
                logger.log(Level.INFO, "Number of entries removed from log {0}", trimResponse.getTrimCount());
            }

            @Override
            public void onError(Throwable throwable) {
                Status status = Status.fromThrowable(throwable);
                logger.log(Level.WARNING, "Trim log failed: {0}", status);
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.log(Level.INFO, "Finished trimming");
                finishLatch.countDown();
            }
        };

        StreamObserver<TrimRequest> requestObserver = trimAsyncStub.trimLog(responseObserver);
        try {
            for (DurabilityKey durabilityKey: trimList) {
                requestObserver.onNext(TrimRequest.newBuilder()
                                                  .setClientId(durabilityKey.getClientId())
                                                  .setRequestId(durabilityKey.getRequestId())
                                                  .build());
                Thread.sleep(1000);
                if (finishLatch.getCount() == 0) {
                    return;
                }
            }
        } catch (StatusRuntimeException e) {
            requestObserver.onError(e);
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        requestObserver.onCompleted();
        try {
            finishLatch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    public static void main(String[] args) throws Exception{

    }
}