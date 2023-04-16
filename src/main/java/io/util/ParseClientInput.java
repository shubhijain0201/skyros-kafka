package io.util;
import org.apache.commons.cli.*;
import org.ietf.jgss.GSSName;

public class ParseClientInput {

    private static Options options;
    public ParseClientInput() {
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

        Option inputFile = Option.builder("i").longOpt("input_file")
                .argName("input_file")
                .hasArg(true)
                .required(false)
                .desc("File containing input data to be written to Kafka. Use this if you want to provide" +
                        "the input from a file and not through the command line")
                .build();
        options.addOption(inputFile);

        Option topic = Option.builder("t").longOpt("topic")
                .argName("topic")
                .hasArg(true)
                .required(true)
                .desc("Kafka topic to write data to and read data from.")
                .build();
        options.addOption(topic);

        Option numberOfRecords = Option.builder("n").longOpt("num_records")
                .argName("num_records")
                .hasArg(true)
                .required(false)
                .desc("The number of records to read from Kafka.")
                .build();
        options.addOption(numberOfRecords);

        Option operation = Option.builder("o").longOpt("operation")
                .argName("operation")
                .hasArg(true)
                .required(true)
                .desc("The operation the client wants to perform:, put, get or update")
                .build();
        options.addOption(operation);

        Option config = Option.builder("c").longOpt("config_file")
                .argName("config_file")
                .hasArg(true)
                .required(true)
                .desc("The file containing cluster configuration information")
                .build();
        options.addOption(config);

        Option opType = Option.builder("op").longOpt("operation_type")
                .argName("operation_type")
                .hasArg(true)
                .required(false)
                .desc("The kind of operation being performed: \n " +
                        "w_all : write with acks = all \n" +
                        "w_1 : write with acks = 1 \n w_0 : write with acks = 0")
                .build();
        options.addOption(opType);

        Option clientId = Option.builder("c_id").longOpt("client_id")
                .argName("client_id")
                .hasArg(true)
                .required(false)
                .desc("The ID of the current client")
                .build();
        options.addOption(clientId);

        Option parseKey = new Option("parse_key", true,
                          "Whether the key has been provided by the client");
        options.addOption(parseKey);

        Option keySeparator = Option.builder("key_sep").longOpt("key_separator")
                .argName("key_separator")
                .hasArg(true)
                .required(false)
                .desc("The separator between key and value if key is provided")
                .build();
        options.addOption(keySeparator);

        Option timeout = Option.builder("tm").longOpt("timeout")
                .argName("timeout")
                .hasArg(true)
                .required(false)
                .desc("The timeout for consumer in seconds")
                .build();
        options.addOption(timeout);

        Option offset = Option.builder("offset")
                .argName("offset")
                .hasArg(true)
                .required(false)
                .desc("The offset from which to consume records. Default is from the beginning.")
                .build();
        options.addOption(offset);
    }
}
