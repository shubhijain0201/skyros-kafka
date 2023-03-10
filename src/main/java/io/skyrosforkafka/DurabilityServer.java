package io.skyrosforkafka;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import io.util.Pair;

import java.io.IOException;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;
public class DurabilityServer {

    private static final Logger logger = Logger.getLogger(RPCServer.class.getName());

    private SortedMap<Integer, Pair> durabilityMap;

    private RPCServer rpcServer;

    public DurabilityServer(String ip) {
        durabilityMap = new TreeMap<>();
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

    public PutResponse putInDurability(String key, String value) {
        int index = 1;
        if (!durabilityMap.isEmpty()) {
            index = durabilityMap.lastKey() + 1;
        }
        durabilityMap.put(index, new Pair(key, value));

        PutResponse response = PutResponse.newBuilder().setValue("Hello, sending an ack" ).build();
        return response;
    }

    public static void main(String args[]){

        String target = "10.10.1.5:50051";

        if (args.length > 0) {
            if ("--help".equals(args[0])) {
                System.err.println("Usage: [ip:port]");
                System.err.println("");
                System.err.println("  ip:port  IP and port the server connects to. Defaults to " + target);
                System.exit(1);
            }
            target = args[0];
        }

        DurabilityServer durabilityServer = new DurabilityServer(target);
        logger.info("Listening to requests...");
    }
}
