package ru.spb.itmo.pirsbd.asashina.replication.crdt.command;

public record RemoteOperationCommand(CrdtOperation operation) implements ReplicaCommand {
}
