package ru.spb.itmo.pirsbd.asashina.replication.crdt.command;

import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.VersionVector;

public record PutOperation(
        String operationId,
        String originReplica,
        long originCounter,
        VersionVector versionVector,
        String key,
        long originAcceptedAtMillis,
        String value
) implements CrdtOperation {

    @Override
    public OperationType operationType() {
        return OperationType.PUT;
    }

}
