package io.common;

import io.util.DurabilityKey;
import io.util.DurabilityValue;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

public class CommonReplica {

    public static boolean isNilext(String opType) {
        return opType.equals("w_all") || opType.equals("w_1");
    }

    public static List<DurabilityKey> backgroundReplication(ConcurrentLinkedQueue<MutablePair<DurabilityKey, DurabilityValue>> dataQueue) {
        Queue <MutablePair<DurabilityKey, DurabilityValue>> tempQueue = getAndDeleteQueue(dataQueue);
        MutablePair<DurabilityKey, DurabilityValue> tempValue;
        List<DurabilityKey> trimList = new ArrayList<>();
        while (!tempQueue.isEmpty()) {
            trimList.add(tempQueue.poll().getLeft());
            // handle temp value;
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
