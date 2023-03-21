package io.skyrosforkafka;

import io.util.ClientRequest;
import io.util.Configuration;
import io.util.ParseClientInput;
import org.apache.commons.cli.CommandLine;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

public class KafkaClient {

    private static final Logger logger = Logger.getLogger(KafkaClient.class.getName());
    private RPCClient rpcClient;
    private String inputMessage;
    private static Configuration configuration;
    private int requestId;
    private int quorum;
    private ClientRequest clientRequest;
    private ConcurrentHashMap<Integer, Integer> receivedResponses;
    private ConcurrentHashMap<Integer, Boolean> leaderResponse;
    private Scanner sc;
    private static long clientId;

    public KafkaClient (List <String> serverIPs, File inputData) {
        logger.setLevel(Level.ALL);

        requestId = 0;
        quorum = configuration.getQuorum();
        receivedResponses = new ConcurrentHashMap<>();
        leaderResponse = new ConcurrentHashMap<>();

        if(inputData != null) {
            try {
                sc = new Scanner(inputData);
            } catch (FileNotFoundException e) {
                logger.log(Level.SEVERE, "Input file not provided");
                System.exit(1);
            }
        } else {
            sc = new Scanner(System.in);
        }

        //for(int i = 0; i < serverIPs.size(); i++)
        rpcClient = new RPCClient(serverIPs.get(0));
    }

    private void put(ClientRequest clientRequest) {
        logger.log(Level.INFO, "In kafka put");
        rpcClient.put(clientRequest, this);
    }

    public void handleReply(PutResponse putResponse) {

        logger.info("Received response from server " + putResponse.getReplicaIndex() + " for request " + requestId
                + "with value = " + putResponse.getValue());

        if(receivedResponses.get(requestId) != null) {
            int responseCount = receivedResponses.get(requestId);
            receivedResponses.put(requestId, responseCount + 1);
        } else {
            receivedResponses.put(requestId, 1);
        }

        if(putResponse.getReplicaIndex() == Configuration.getLeader()) {
            leaderResponse.put(requestId, true);
        }

        if(receivedResponses.get(requestId) >= quorum &&
            leaderResponse.get(requestId)) {
            SendNext();
        }
    }

    private void SendNext() {
        logger.log(Level.INFO, "In send");
        if (sc.hasNextLine()) {
            incrementRequestId();
            inputMessage = sc.nextLine();

            clientRequest.setMessage(inputMessage);
            clientRequest.setRequestId(requestId);
            put(clientRequest);
        }
    }
    private void incrementRequestId() {
        this.requestId = this.requestId + 1;
    }
    public static void main(String args[]) {
        String config = "";
        boolean parseKey = true;
        String keySeparator = null;
        String opType = null;
        clientId = 0;
        File inputData;

        ParseClientInput parseClientInput = new ParseClientInput();
        logger.log(Level.INFO, "Argument read " + args[0]);
        CommandLine commandLine = parseClientInput.parseOptions(args);

        if(commandLine.hasOption("c")) {
            config = commandLine.getOptionValue("c");
        }

        if(commandLine.hasOption("op")) {
            opType = commandLine.getOptionValue("op");
            if(!opType.equals("w_all") && !opType.equals("w_1") && !opType.equals("w_0") && !opType.equals("r")) {
                // error, exit
            }
        }

        if(commandLine.hasOption("c_id")) {
            clientId = Long.parseLong(commandLine.getOptionValue("c_id"));
        }

        if(commandLine.hasOption("parse_key")) {
            parseKey = Boolean.parseBoolean(commandLine.getOptionValue("parse_key"));
        }

        if(commandLine.hasOption("key_sep")) {
            keySeparator = commandLine.getOptionValue("key_sep");
        }

        if(commandLine.hasOption("i")) {
            inputData = new File(commandLine.getOptionValue("i"));
        } else {
            inputData = null;
        }


        configuration = new Configuration(config);
        List<String> serverIPs = configuration.getServerIPs();

        KafkaClient kafkaClient = new KafkaClient(serverIPs, inputData);

        kafkaClient.clientRequest = new ClientRequest(clientId, parseKey, keySeparator, opType);

        kafkaClient.clientRequest.setMessage(kafkaClient.sc.nextLine());
        kafkaClient.clientRequest.setRequestId(kafkaClient.requestId);

        kafkaClient.put(kafkaClient.clientRequest);


        // for(int i = 1; i <= 10; i++) {


            
        // }
    }
}
