package io.skyrosforkafka;

import io.common.CommonReplica;
import io.util.Configuration;
import io.util.DurabilityKey;
import io.util.DurabilityValue;

import java.io.IOException;
import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;
public class DurabilityServer {

    private static final Logger logger = Logger.getLogger(DurabilityServer.class.getName());

    private ConcurrentSkipListMap<DurabilityKey, DurabilityValue> durabilityMap;

    private ConcurrentLinkedQueue<DurabilityValue> dataQueue;

    private final int myIndex;

    private final RPCServer rpcServer;

    public DurabilityServer(String ip, int index) {
        logger.setLevel(Level.ALL);

        durabilityMap = new ConcurrentSkipListMap<>(durabilityKeyComparator);
        myIndex = index;
        rpcServer = new RPCServer(this);
        try {
            rpcServer.start(Integer.parseInt(ip.split(":")[1]));
            rpcServer.blockUntilShutdown();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    Comparator<DurabilityKey> durabilityKeyComparator = (key1, key2) -> {
        Integer index1 = key1.getIndex();
        Integer index2 = key2.getIndex();
        return index1.compareTo(index2);
    };
    public PutResponse putInDurability(PutRequest putRequest) {

        if(!CommonReplica.isNilext(putRequest.getOpType())) {
            if(!amILeader()) {
                PutResponse response = PutResponse.newBuilder()
                        .setValue("op_not_done")
                        .setReplicaIndex(myIndex)
                        .build();

                return response;
            }
        }

        DurabilityKey durabilityKey = new DurabilityKey(putRequest.getClientId(), putRequest.getRequestId());
        DurabilityValue durabilityValue = new DurabilityValue(putRequest.getMessage(), putRequest.getParseKey(),
                putRequest.getKeySeparator());
        logger.log(Level.INFO, "Message received: " + putRequest.getMessage());
        durabilityMap.put(durabilityKey, durabilityValue);

        if(amILeader()) {
            dataQueue.add(durabilityValue);
        }

        PutResponse response = PutResponse.newBuilder()
                                          .setValue("dur-ack" )
                                          .setReplicaIndex(myIndex)
                                          .build();
        return response;
    }

    private boolean amILeader() {
        return myIndex == Configuration.getLeader();
    }

    public static void main(String args[]){

        String target = "0.0.0.0:50051";
        int index = 1;

        if (args.length > 0) {
            if ("--help".equals(args[0])) {
                System.err.println("Usage: [ip:port]");
                System.err.println("");
                System.err.println("  ip:port  IP and port the server connects to. Defaults to " + target);
                System.exit(1);
            }
            target = args[0];
        }
        logger.log(Level.INFO, "Listening to requests...");

        DurabilityServer durabilityServer = new DurabilityServer(target, index);

    }
}
