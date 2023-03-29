package io.util;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;

public class ReplicaUtil {

    private static final String KAFKA_SERVER = "bootstrap.server";
    private static final String RESET_CONFIG = "offset.reset.config";
    private static String getRandomGroupId() {
        Random random = new Random();
        String generatedString = random.ints(97, 123)
                .limit(10)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        return generatedString;
    }

    public static void setConsumerProperties(Properties properties, String propertyFile) {

        try {
            InputStream input = new FileInputStream(propertyFile);

            Properties props = new Properties();

            // load a properties file
            props.load(input);

            // get the property value and print it out
            properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getProperty(KAFKA_SERVER));
            properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,props.getProperty(RESET_CONFIG));

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, getRandomGroupId());
    }
}
