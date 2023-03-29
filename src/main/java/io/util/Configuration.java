package io.util;

import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class Configuration {

    private File configFile;
    private int quorum;
    private int leaderId;
    private List<String> serverIPs;


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

            int serverCount = Integer.parseInt(properties.getProperty("server.count"));
            for(int i = 1; i <= serverCount; i++) {
                serverIPs.add(properties.getProperty("server" + i));
            }

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

}
