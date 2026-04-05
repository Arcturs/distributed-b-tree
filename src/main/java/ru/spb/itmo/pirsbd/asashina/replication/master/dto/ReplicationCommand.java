package ru.spb.itmo.pirsbd.asashina.replication.master.dto;

import ru.spb.itmo.pirsbd.asashina.replication.master.dictionary.OperationType;

public record ReplicationCommand(
        String operationId,
        OperationType operationType,
        String key,
        String value,
        long masterReceivedAtMillis
) {
}
