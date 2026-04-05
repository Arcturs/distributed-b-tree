package ru.spb.itmo.pirsbd.asashina.replication.crdt.state;

import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.GetResult;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.metrics.CrdtMetrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReadSnapshot {

    private final CrdtMetrics crdtMetrics;
    private final ConcurrentMap<String, GetResult> index = new ConcurrentHashMap<>();
    private final AtomicInteger conflictKeys = new AtomicInteger();

    public ReadSnapshot(CrdtMetrics crdtMetrics) {
        this.crdtMetrics = crdtMetrics;
    }

    public GetResult get(String key) {
        return index.getOrDefault(key, GetResult.absent(key));
    }

    public boolean exists(String key) {
        return get(key).exists();
    }

    public void put(String key, GetResult result) {
        var previous = index.put(key, result);
        var previousConflict = previous != null && previous.conflict();
        var nextConflict = result.conflict();

        if (!previousConflict && nextConflict) {
            conflictKeys.incrementAndGet();
            crdtMetrics.incrementConflictKeys();
        } else if (previousConflict && !nextConflict) {
            conflictKeys.decrementAndGet();
            crdtMetrics.decrementConflictKeys();
        }
    }

    public int conflictKeys() {
        return conflictKeys.get();
    }

}
