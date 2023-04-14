package io.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class ReplicaUtil {

  private static final String KAFKA_SERVER = "bootstrap.servers";
  private static final String RESET_CONFIG = "offset.reset.config";

  private static final String ACKS = "acks";
  private static final String RETRIES = "retries";
  private static final String BATCH_SIZE = "batch.size";
  private static final String LINGER_MS = "linger.ms";
  private static final String BUFFER_MEMORY = "buffer.memory";

  private static String getRandomGroupId() {
    Random random = new Random();
    String generatedString = random
      .ints(97, 123)
      .limit(10)
      .collect(
        StringBuilder::new,
        StringBuilder::appendCodePoint,
        StringBuilder::append
      )
      .toString();
    return generatedString;
  }

  public static void setConsumerProperties(
    Properties properties,
    String propertyFile
  ) {
    try {
      InputStream input = new FileInputStream(propertyFile);

      Properties props = new Properties();

      // load a properties file
      props.load(input);

      // get the property value and print it out
      properties.setProperty(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        props.getProperty(KAFKA_SERVER)
      );
      properties.setProperty(
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
        props.getProperty(RESET_CONFIG)
      );
    } catch (FileNotFoundException ex) {
      ex.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    properties.setProperty(
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
      StringDeserializer.class.getName()
    );
    properties.setProperty(
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
      StringDeserializer.class.getName()
    );
    properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, getRandomGroupId());
  }

  public static void setProducerProperties(
    Properties properties,
    String propertyFile,
    String acks
  ) {
    try {
      InputStream input = new FileInputStream(propertyFile);

      Properties props = new Properties();

      // load a properties file
      props.load(input);

      // get the property value and print it out
      properties.setProperty(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
        props.getProperty(KAFKA_SERVER)
      );
      properties.setProperty(
        ProducerConfig.RETRIES_CONFIG,
        props.getProperty(RETRIES)
      );
      properties.setProperty(
        ProducerConfig.BATCH_SIZE_CONFIG,
        props.getProperty(BATCH_SIZE)
      );
      properties.setProperty(
        ProducerConfig.LINGER_MS_CONFIG,
        props.getProperty(LINGER_MS)
      );
      properties.setProperty(
        ProducerConfig.BUFFER_MEMORY_CONFIG,
        props.getProperty(BUFFER_MEMORY)
      );
    } catch (FileNotFoundException ex) {
      ex.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    properties.setProperty(ProducerConfig.ACKS_CONFIG, acks);
    properties.setProperty(
      ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
      StringSerializer.class.getName()
    );
    properties.setProperty(
      ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
      StringSerializer.class.getName()
    );
  }
}
