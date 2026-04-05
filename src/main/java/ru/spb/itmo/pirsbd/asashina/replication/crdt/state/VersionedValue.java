package ru.spb.itmo.pirsbd.asashina.replication.crdt.state;

public record VersionedValue<V>(
        String operationId,
        String originReplica,
        long originCounter,
        VersionVector versionVector,
        V value,
        boolean tombstone
) {
}
