package io.common;

import io.util.DurabilityKey;
import io.util.DurabilityValue;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

public class CommonReplica {

    public static boolean isNilext(String opType) {
        return opType.equals("w_all") || opType.equals("w_1");
    }

    public int backgroundReplication(ConcurrentLinkedQueue<DurabilityValue> dataQueue) {
        DurabilityValue tempValue;
        int queueSize = dataQueue.size();
        while (!dataQueue.isEmpty()) {
            tempValue = dataQueue.poll();
             //handle temp value;
        }
        return queueSize;
    }

    public static void upCall(String opType) {

        if(isNilext(opType)) {

        }

    }

}
