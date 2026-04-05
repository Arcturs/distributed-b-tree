package ru.spb.itmo.pirsbd.asashina.replication.crdt.state;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record VersionVector(Map<String, Long> entries) {

    public VersionVector {
        entries = Map.copyOf(entries);
    }

    public long get(String replicaId) {
        return entries.getOrDefault(replicaId, 0L);
    }

    public Relation relate(VersionVector other) {
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(this.entries.keySet());
        allKeys.addAll(other.entries.keySet());

        var less = false;
        var greater = false;
        for (var key : allKeys) {
            var left = this.get(key);
            var right = other.get(key);

            if (left < right) {
                less = true;
            }
            if (left > right) {
                greater = true;
            }

            if (less && greater) {
                return Relation.CONCURRENT;
            }
        }

        if (!less && !greater) {
            return Relation.EQUAL;
        }
        if (less) {
            return Relation.BEFORE;
        }
        return Relation.AFTER;
    }

    public enum Relation {
        BEFORE,
        AFTER,
        EQUAL,
        CONCURRENT
    }

}
