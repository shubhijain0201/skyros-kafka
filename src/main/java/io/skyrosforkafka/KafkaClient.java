package io.skyrosforkafka;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class KafkaClient {

    private static final Logger logger = Logger.getLogger(KafkaClient.class.getName());

    private final RPCClient rpcClient;

    public KafkaClient (List <String> serverIPs) {
        rpcClient = new RPCClient(serverIPs.get(0));
    }

    public void put(String key, String value) {
        rpcClient.put(key, value);
    }
    public static void main(String args[]) {
        String key = null;
        String value = "";
        String target = "10.10.1.5:50051";

        List<String> serverIPs = new ArrayList<>();

        if (args.length > 0) {
            if ("--help".equals(args[0])) {
                System.err.println("Usage: [key][value][target]");
                System.err.println("");
                System.err.println("  key    Optional key for the value. Defaults to " + key);
                System.err.println(" value   The value you want to write. Defaults to " + value);
                System.err.println("  target  The server to connect to. Defaults to " + target);
                System.exit(1);
            }
            key = args[0];
            value = args[1];
        }
        if (args.length > 2) {
            target = args[2];
        }

        serverIPs.add(target);
        KafkaClient kafkaClient = new KafkaClient(serverIPs);
        for(int i = 1; i <= 10; i++) {
            kafkaClient.put(key, value);
        }
    }
}
