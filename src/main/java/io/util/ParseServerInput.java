package io.util;

import org.apache.commons.cli.*;

public class ParseServerInput {

    private static Options options;
    public ParseServerInput() {
        options = new Options();
        addOptions();
    }

    public CommandLine parseOptions(String args[]) {
        CommandLineParser parser = new GnuParser();
        try {
            CommandLine line = parser.parse(options, args);
            return line;
        } catch (ParseException e) {
            System.err.println("Parsing failed \n");
            e.printStackTrace();
        }
        return null;
    }
    private void addOptions() {

        Option help = new Option("help", "usage: --<option> <value>");
        options.addOption(help);

        Option config = Option.builder("c").longOpt("config")
                .argName("config")
                .hasArg(true)
                .required(true)
                .desc("The config file to get the server configuration fro")
                .build();
        options.addOption(config);

        Option target = Option.builder("t").longOpt("target")
                .argName("target")
                .hasArg(true)
                .required(true)
                .desc("The IP:port of this server")
                .build();
        options.addOption(target);

        Option serverId = Option.builder("s_id").longOpt("server_id")
                .argName("server_id")
                .hasArg(true)
                .required(true)
                .desc("The ID of this server")
                .build();
        options.addOption(serverId);

        Option consumerConfig = Option.builder("k").longOpt("consumer_config")
                .argName("consumer_config")
                .hasArg(true)
                .required(false)
                .desc("Property file containing Kafka consumer properties")
                .build();
        options.addOption(consumerConfig);
    }
}
