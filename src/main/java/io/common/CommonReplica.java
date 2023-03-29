package io.common;

import io.util.DurabilityKey;
import io.util.DurabilityValue;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Logger;
import java.util.logging.Level;

public class CommonReplica {

    private static final Logger logger = Logger.getLogger(CommonReplica.class.getName());

    public static boolean isNilext(String opType) {
        return opType.equals("w_all") || opType.equals("w_1");
    }

    public static long backgroundReplication(ConcurrentLinkedQueue<DurabilityValue> dataQueue) {
        Queue <DurabilityValue> tempQueue = getAndDeleteQueue(dataQueue);
        DurabilityValue tempValue;
        long queueSize = tempQueue.size();
        while (!tempQueue.isEmpty()) {
            tempValue = tempQueue.poll();
            // handle temp value;
        }
        return queueSize;
    }

    public static long clearDurabilityLogTillOffset(long offset, ConcurrentSkipListMap<DurabilityKey, DurabilityValue> durabilityMap) {
        Iterator<Map.Entry<DurabilityKey, DurabilityValue>> iterator = durabilityMap.entrySet().iterator();
        long recordsRemoved = 0;
        while (offset > 0 && iterator.hasNext()) {
            iterator.remove();
            offset--;
            recordsRemoved ++;
        }
        return recordsRemoved;
    }

    public static Queue<DurabilityValue> getAndDeleteQueue(ConcurrentLinkedQueue<DurabilityValue> dataQueue) {
        Queue<DurabilityValue> tempQueue = new LinkedList<>();
        DurabilityValue tempValue;

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
