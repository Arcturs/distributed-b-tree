package ru.spb.itmo.pirsbd.asashina.replication.master.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RecentOperationStore {

    private final ConcurrentMap<String, Boolean> recent;

    public RecentOperationStore(int capacity) {
        this.recent = new ConcurrentHashMap<>(capacity);
    }

    public boolean markIfNew(String operationId) {
        if (recent.containsKey(operationId)) {
            return false;
        }
        recent.putIfAbsent(operationId, true);
        return true;
    }

}
