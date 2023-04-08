package io.kafka;

import io.grpc.stub.StreamObserver;
import io.skyrosforkafka.GetResponse;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public class ConsumeRecords implements Callable<GetResponse> {

  private static final Logger logger = Logger.getLogger(
    ConsumeRecords.class.getName()
  );
  private KafkaConsumer<String, String> kafkaConsumer;
  private long numRecords;
  StreamObserver<GetResponse> responseObserver;

  public ConsumeRecords(
    KafkaConsumer<String, String> kafkaConsumer,
    long numRecords,
    StreamObserver<GetResponse> responseObserver
  ) {
    this.kafkaConsumer = kafkaConsumer;
    this.numRecords = numRecords;
    this.responseObserver = responseObserver;
  }

  public GetResponse call() {
    int readRecords = 0;

    while (true) {
      ConsumerRecords<String, String> consumerRecords = kafkaConsumer.poll(
        Duration.ofMillis(1000)
      );

      for (ConsumerRecord<String, String> record : consumerRecords) {
        logger.info("cONSUMING RECORDS..");
        GetResponse response = GetResponse
          .newBuilder()
          .setValue("Key: " + record.key() + ", Value: " + record.value())
          .build();
        responseObserver.onNext(response);
      }
      readRecords = readRecords + consumerRecords.count();

      if (readRecords > numRecords && numRecords > 0) {
        break;
      }
    }
    return null;
  }
}
