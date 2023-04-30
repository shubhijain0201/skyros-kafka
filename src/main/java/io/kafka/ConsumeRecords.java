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
    boolean keepReading = true;

    while (keepReading) {
      ConsumerRecords<String, String> consumerRecords = kafkaConsumer.poll(
        Duration.ofMillis(50)
      );

      for (ConsumerRecord<String, String> record : consumerRecords) {
        readRecords = readRecords + 1;
        //logger.info("CONSUMING RECORDS..");
        GetResponse response = GetResponse
          .newBuilder()
          .setValue("Key: " + record.key() + ", Value: " + record.value())
          .build();
        // logger.info("CONSUMING RECORDS.." + readRecords);
        responseObserver.onNext(response);

        if (readRecords > numRecords && numRecords > 0) {
          // logger.info("here..");
          keepReading = false;
          break;
        }
      }
      if(!keepReading) {
        responseObserver.onCompleted();
      }
    }
    return null;
  }
}
