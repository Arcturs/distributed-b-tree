package ru.spb.itmo.pirsbd.asashina.replication.crdt.command;

import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.VersionVector;

public record RemoveOperation(
        String operationId,
        String originReplica,
        long originCounter,
        VersionVector versionVector,
        String key,
        long originAcceptedAtMillis
) implements CrdtOperation {

    @Override
    public OperationType operationType() {
        return OperationType.REMOVE;
    }

}
