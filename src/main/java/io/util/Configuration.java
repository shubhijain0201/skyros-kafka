package io.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Configuration {

    private File configFile;
    private int quorum;

    private static int leaderId;

    private final List<String> serverIPs;


    public Configuration(String configFile) {
        this.configFile = new File(configFile);
        serverIPs = new ArrayList<>();
        setConfig();
    }

    private void setConfig() {
        try {
            Scanner configReader = new Scanner(configFile);

            while (configReader.hasNextLine()) {
                String data = configReader.nextLine();

                if(data.contains(":")) {
                    serverIPs.add(data);
                } else if(data.contains("f")) {
                    String [] quorumData = data.split(" ");
                    quorum = Integer.parseInt(quorumData[1]);
                } else if(data.contains("l")) {
                    String leaderData[] = data.split(" ");
                    leaderId = Integer.parseInt(leaderData[1]);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public int getQuorum() {
        return quorum;
    }

    public static int getLeader() {
        return leaderId;
    }

    public List<String> getServerIPs() {
        return serverIPs;
    }

}
