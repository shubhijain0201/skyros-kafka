package io.skyrosforkafka;

import io.util.ClientPutRequest;
import io.util.Configuration;
import io.util.ParseClientInput;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;

public class KafkaClient {

  private static final Logger logger = Logger.getLogger(
    KafkaClient.class.getName()
  );
  private RPCClient rpcClient;
  private String inputMessage;
  private static Configuration configuration;
  private int requestId;
  private int quorum;
  private ClientPutRequest clientPutRequest;
  //   private ConcurrentHashMap<Integer, Integer> receivedResponses;
  //   private ConcurrentHashMap<Integer, Boolean> leaderResponse;
  private Scanner sc;
  private static long clientId;

  public KafkaClient(List<String> serverIPs, int port) {
    logger.setLevel(Level.ALL);

    //for(int i = 0; i < serverIPs.size(); i++)
    rpcClient = new RPCClient(serverIPs, port);
  }

  private void put(ClientPutRequest clientPutRequest) {
    logger.log(Level.INFO, "In kafka put");
    try {
      rpcClient.put(clientPutRequest, this, configuration.getLeader());
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, "Put failed!", e);
    }
  }

  //   public void handlePutReply(PutResponse putResponse) {
  //     logger.info(
  //       "Received response from server " +
  //       putResponse.getReplicaIndex() +
  //       " for request " +
  //       putResponse.getRequestId() +
  //       "with value = " +
  //       putResponse.getValue()
  //     );

  //     if (
  //       putResponse.getValue().equals("op_not_done") ||
  //       putResponse.getValue().equals("sent to Kafka!")
  //     ) {
  //       logger.log(Level.INFO, "Nothing to do!");
  //     } else {
  //       if (receivedResponses.get(requestId) != null) {
  //         int responseCount = receivedResponses.get(requestId);
  //         receivedResponses.put(requestId, responseCount + 1);
  //       } else {
  //         logger.info("Request here");
  //         receivedResponses.put(requestId, 1);
  //         logger.info("Request here done");
  //       }

  //       if (putResponse.getReplicaIndex() == configuration.getLeader()) {
  //         logger.info("Leader response");
  //         leaderResponse.put(requestId, true);
  //         logger.info("Leader response done");
  //       }

  //       if (
  //         receivedResponses.get(requestId) >= quorum &&
  //         leaderResponse.get(requestId)
  //       ) {
  //         SendNext();
  //       }
  //     }
  //   }

  public void get(String topic, long numRecords, long timeout) {
    rpcClient.get(topic, numRecords, timeout, this);
  }

  public void handleGetReply(Iterator<GetResponse> response) {
    while (response.hasNext()) {
      if (response.next().getValue().equals("op_not_done")) {
        continue;
      }
      GetResponse getResponse = response.next();
      logger.log(Level.INFO, "Received data: {0}", getResponse.getValue());
    }
  }

  private void initForPut(File inputfFile) {
    requestId = 0;
    quorum = configuration.getQuorum();
    // receivedResponses = new ConcurrentHashMap<>();
    // leaderResponse = new ConcurrentHashMap<>();
    readFromFile(inputfFile);
  }

  private void readFromFile(File inputFile) {
    if (inputFile != null) {
      try {
        sc = new Scanner(inputFile);
      } catch (FileNotFoundException e) {
        logger.log(Level.SEVERE, "Input file does not exist. Exit!");
        System.exit(1);
      }
    } else {
      sc = new Scanner(System.in);
    }
  }

  public void SendNext() {
    logger.log(Level.INFO, "In send");
    if (sc.hasNextLine()) {
      incrementRequestId();
      inputMessage = sc.nextLine();

      clientPutRequest.setMessage(inputMessage);
      clientPutRequest.setRequestId(requestId);
      put(clientPutRequest);
    }
  }

  private void incrementRequestId() {
    this.requestId = this.requestId + 1;
  }

  public static void main(String args[]) {
    String config = "";
    boolean parseKey = false;
    String keySeparator = null;
    String opType = null;
    String topic = null;
    long numberOfRecords = -1;
    long timeout = 10;
    clientId = 0;
    File inputData;
    String operation = null;

    ParseClientInput parseClientInput = new ParseClientInput();
    logger.log(Level.INFO, "Argument read " + args[0]);
    CommandLine commandLine = parseClientInput.parseOptions(args);

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
    int serverPort = configuration.getServerPort();

    KafkaClient kafkaClient = new KafkaClient(serverIPs, serverPort);

    if (commandLine.hasOption("o")) {
      operation = commandLine.getOptionValue("o");
    } else {
      logger.log(
        Level.SEVERE,
        "Operation to be performed not provided, cannot do anything. Exit!"
      );
      System.exit(1);
    }

    if (operation.equals("put")) {
      if (commandLine.hasOption("op")) {
        opType = commandLine.getOptionValue("op");
        if (
          !opType.equals("w_all") &&
          !opType.equals("w_1") &&
          !opType.equals("w_0")
        ) {
          logger.log(
            Level.SEVERE,
            "Correct opType not provided, cannot do anything. Exit!"
          );
          System.exit(1);
        }
      } else {
        logger.log(
          Level.SEVERE,
          "opType not provided, cannot do anything. Exit!"
        );
        System.exit(1);
      }

      if (commandLine.hasOption("c_id")) {
        clientId = Long.parseLong(commandLine.getOptionValue("c_id"));
      } else {
        logger.log(
          Level.SEVERE,
          "ClientId not provided, cannot perform any put operation. Exit!"
        );
        System.exit(1);
      }

      if (commandLine.hasOption("parse_key")) {
        parseKey =
          Boolean.parseBoolean(commandLine.getOptionValue("parse_key"));
      } else {
        logger.log(
          Level.WARNING,
          "Presence of key not indicated, assuming no key."
        );
      }

      if (parseKey) {
        if (commandLine.hasOption("key_sep")) {
          keySeparator = commandLine.getOptionValue("key_sep");
        } else {
          logger.log(
            Level.SEVERE,
            "Key separator not provided, cannot distinguish key from value. Exit!"
          );
          System.exit(1);
        }
      }

      if (commandLine.hasOption("i")) {
        inputData = new File(commandLine.getOptionValue("i"));
      } else {
        inputData = null;
      }

      if (commandLine.hasOption("t")) {
        topic = commandLine.getOptionValue("t");
      } else {
        logger.log(
          Level.SEVERE,
          "Topic to be written to not provided, cannot write to random topic. Exit!"
        );
        System.exit(1);
      }

      kafkaClient.initForPut(inputData);
      kafkaClient.clientPutRequest =
        new ClientPutRequest(clientId, parseKey, keySeparator, opType, topic);

      kafkaClient.clientPutRequest.setMessage(kafkaClient.sc.nextLine());
      kafkaClient.clientPutRequest.setRequestId(kafkaClient.requestId);

      kafkaClient.put(kafkaClient.clientPutRequest);
    } else if (operation.equals("get")) {
      if (commandLine.hasOption("t")) {
        topic = commandLine.getOptionValue("t");
      } else {
        logger.log(
          Level.SEVERE,
          "Topic to be read from not provided, cannot read from random topic. Exit!"
        );
        System.exit(1);
      }

      if (commandLine.hasOption("n")) {
        numberOfRecords = Long.parseLong(commandLine.getOptionValue("n"));
      } else {
        logger.log(
          Level.INFO,
          "Reading all the records added to Kafka till now"
        );
      }

      if (commandLine.hasOption("tm")) {
        timeout = Long.parseLong(commandLine.getOptionValue("tm"));
      } else {
        logger.log(
          Level.INFO,
          "No timeout provided, using default timeout of 10 seconds."
        );
      }

      kafkaClient.get(topic, numberOfRecords, timeout);
    }
  }
}
