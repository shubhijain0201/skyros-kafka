package io.util;

import java.io.*;
import java.lang.System;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import org.apache.kafka.clients.consumer.ConsumerConfig;

public class Configuration {

  private File configFile;
  private int quorum;
  private int leaderId;
  private List<String> serverIPs;
  private int serverPort;

  public Configuration(String configFile) {
    this.configFile = new File(configFile);
    serverIPs = new ArrayList<>();
    setConfig();
  }

  private void setConfig() {
    try {
      InputStream input = new FileInputStream(configFile);

      Properties properties = new Properties();

      // load a properties file
      properties.load(input);

      quorum = Integer.parseInt(properties.getProperty("quorum"));
      leaderId = Integer.parseInt(properties.getProperty("leader.id"));
      serverPort = Integer.parseInt(properties.getProperty("server.port"));
      int serverCount = Integer.parseInt(
        properties.getProperty("server.count")
      );
      String ipsString = properties.getProperty("server.ips");
      StringWriter writer = new StringWriter();
      String[] ipsArray = ipsString.split(",");
      for (int i = 0; i < ipsArray.length; i++) {
        ipsArray[i] = ipsArray[i].trim();
      }
      serverIPs = new ArrayList<>(Arrays.asList(ipsArray));
      // Trim each element in the array

    } catch (FileNotFoundException ex) {
      ex.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public int getQuorum() {
    return quorum;
  }

  public int getLeader() {
    return leaderId;
  }

  public List<String> getServerIPs() {
    return serverIPs;
  }

  public int getServerPort() {
    return serverPort;
  }
}
