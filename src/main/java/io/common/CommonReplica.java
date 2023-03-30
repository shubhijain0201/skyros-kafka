package io.common;

import io.util.DurabilityKey;
import io.util.DurabilityValue;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

public class CommonReplica {

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
            // handle temp value;
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
