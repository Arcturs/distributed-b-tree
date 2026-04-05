package ru.spb.itmo.pirsbd.asashina.replication.crdt.dto;

import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.CrdtOperation;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.OperationType;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.PutOperation;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.RemoveOperation;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.VersionVector;

import java.util.Map;

public record CrdtOperationRequest(
        OperationType type,
        String operationId,
        String originReplica,
        long originCounter,
        Map<String, Long> versionVector,
        String key,
        String value,
        long originAcceptedAtMillis
) {

    public static CrdtOperationRequest fromOperation(CrdtOperation operation) {
        return switch (operation) {
            case PutOperation put -> new CrdtOperationRequest(
                    OperationType.PUT,
                    put.operationId(),
                    put.originReplica(),
                    put.originCounter(),
                    put.versionVector().entries(),
                    put.key(),
                    put.value(),
                    put.originAcceptedAtMillis()
            );
            case RemoveOperation remove -> new CrdtOperationRequest(
                    OperationType.REMOVE,
                    remove.operationId(),
                    remove.originReplica(),
                    remove.originCounter(),
                    remove.versionVector().entries(),
                    remove.key(),
                    null,
                    remove.originAcceptedAtMillis()
            );
        };
    }

    public CrdtOperation toOperation() {
        VersionVector vv = new VersionVector(versionVector);
        return switch (type) {
            case PUT -> new PutOperation(
                    operationId,
                    originReplica,
                    originCounter,
                    vv,
                    key,
                    originAcceptedAtMillis,
                    value
            );
            case REMOVE -> new RemoveOperation(
                    operationId,
                    originReplica,
                    originCounter,
                    vv,
                    key,
                    originAcceptedAtMillis
            );
        };
    }
}
