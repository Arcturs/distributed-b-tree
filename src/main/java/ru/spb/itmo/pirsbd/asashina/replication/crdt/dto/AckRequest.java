package ru.spb.itmo.pirsbd.asashina.replication.crdt.dto;

public record AckRequest(
        String operationId,
        String operation,
        String fromReplica,
        boolean success
) {
}
