package io.common;

import io.util.DurabilityKey;
import io.util.DurabilityValue;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.Callback;

public class CommonReplica {

    private static final Logger logger = Logger.getLogger(CommonReplica.class.getName());

    public static boolean isNilext(String opType) {
        return opType.equals("w_all") || opType.equals("w_1");
    }

    public static List<DurabilityKey> backgroundReplication(ConcurrentLinkedQueue<MutablePair<DurabilityKey, DurabilityValue>> dataQueue, KafkaProducer<String, String> producer) {
        Queue <MutablePair<DurabilityKey, DurabilityValue>> tempQueue = getAndDeleteQueue(dataQueue);
        MutablePair<DurabilityKey, DurabilityValue> tempPair;
        DurabilityValue tempValue;
        List<DurabilityKey> trimList = new ArrayList<>();
        while (!tempQueue.isEmpty()) {
            tempPair = tempQueue.poll();
            trimList.add(tempPair.getLeft());
            tempValue = tempPair.getValue();
            String key, value;
            if (tempValue.parseKey) {
                String[] parts = tempValue.message.split(tempValue.keySeparator, 2);
                key = parts[0];
                value = parts[1];
            } else {
                key = null;
                value = tempValue.message;
            }
            producer.send(new ProducerRecord<>(tempValue.topic, key, value), new Callback() {
                public void onCompletion(RecordMetadata metadata, Exception e) {
                    if(e != null) {
                        e.printStackTrace();
                    } else {
                        logger.log(Level.INFO, "Message sent to partition " + metadata.partition() + " with offset " + metadata.offset());
                    }
                }
            }); 
        }
        return trimList;
    }

    public static boolean clearDurabilityLogTillOffset(long clientId, long requestId,
                                                    ConcurrentHashMap<DurabilityKey, DurabilityValue> durabilityMap) {
        DurabilityKey key = new DurabilityKey(clientId, requestId);
        return (durabilityMap.remove(key) != null);
    }

    public static Queue<MutablePair<DurabilityKey, DurabilityValue>> getAndDeleteQueue(
            ConcurrentLinkedQueue<MutablePair<DurabilityKey, DurabilityValue>> dataQueue) {
        Queue<MutablePair<DurabilityKey, DurabilityValue>> tempQueue = new LinkedList<>();
        MutablePair<DurabilityKey, DurabilityValue> tempValue;

        int queueSize = dataQueue.size();
        while (queueSize > 0) {
            if (!dataQueue.isEmpty()) {
                tempValue = dataQueue.poll();
                tempQueue.add(tempValue);
            }
            queueSize--;
            //handle temp value;
        }
        return tempQueue;
    }

}
