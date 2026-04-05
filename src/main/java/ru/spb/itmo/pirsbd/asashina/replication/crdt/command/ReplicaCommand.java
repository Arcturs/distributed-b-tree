package ru.spb.itmo.pirsbd.asashina.replication.crdt.command;

public sealed interface ReplicaCommand permits LocalPutCommand, LocalRemoveCommand, RemoteOperationCommand {
}
