package io.skyrosforkafka;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.util.ClientRequest;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RPCClient {
    private static final Logger logger = Logger.getLogger(RPCClient.class.getName());

    private final SkyrosKafkaImplGrpc.SkyrosKafkaImplBlockingStub blockingStub;

    public RPCClient (String serverIP) {
        ManagedChannel channel = null;
        //try{
            channel = Grpc.newChannelBuilder(serverIP, InsecureChannelCredentials.create())
                        .build();
            blockingStub = SkyrosKafkaImplGrpc.newBlockingStub(channel);
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

    public void put(ClientRequest clientRequest, KafkaClient kafkaClient) {
        logger.info("Try to write the message = " + clientRequest);



        PutRequest request = PutRequest.newBuilder()
                .setMessage(clientRequest.getMessage())
                .setClientId(clientRequest.getClientId())
                .setRequestId(clientRequest.getRequestId())
                .setParseKey(clientRequest.isParseKey())
                .setKeySeparator(clientRequest.getKeySeparator())
                .setOpType(clientRequest.getOpType())
                .build();

        logger.info("Request created!");

        PutResponse response;
        try {
            response = blockingStub.put(request);
            kafkaClient.handleReply(response);

        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Response from server: " + response.getValue());

    }
    public static void main(String[] args) throws Exception{

    }
}