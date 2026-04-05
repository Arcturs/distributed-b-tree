package ru.spb.itmo.pirsbd.asashina.replication.crdt.command;

import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.VersionVector;

public sealed interface CrdtOperation permits PutOperation, RemoveOperation {
    String operationId();
    String originReplica();
    long originCounter();
    VersionVector versionVector();
    String key();
    long originAcceptedAtMillis();
    OperationType operationType();
}
