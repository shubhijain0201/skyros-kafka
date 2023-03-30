package io.common;

import io.util.DurabilityKey;
import io.util.DurabilityValue;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
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

    public static long backgroundReplication(ConcurrentLinkedQueue<MutablePair<DurabilityKey, DurabilityValue>> dataQueue) {
        Queue <MutablePair<DurabilityKey, DurabilityValue>> tempQueue = getAndDeleteQueue(dataQueue);
        MutablePair<DurabilityKey, DurabilityValue> tempValue;
        long maxIndex = 0;
        while (!tempQueue.isEmpty()) {
            tempValue = tempQueue.poll();
            maxIndex = Math.max(maxIndex, tempValue.getLeft().getIndex());
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
                        // clear durability log
                    }
                }
            }); 
        }
        return maxIndex;
    }

    public static long clearDurabilityLogTillOffset(long index, ConcurrentSkipListMap<DurabilityKey, DurabilityValue> durabilityMap) {
        Iterator<Map.Entry<DurabilityKey, DurabilityValue>> iterator = durabilityMap.entrySet().iterator();
        long recordsRemoved = 0;
        while (iterator.hasNext() && durabilityMap.firstKey().getIndex() <= index) {
            iterator.remove();
            recordsRemoved ++;
        }
        return recordsRemoved;
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
