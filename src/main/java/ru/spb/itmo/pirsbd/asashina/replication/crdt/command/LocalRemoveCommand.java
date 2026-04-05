package ru.spb.itmo.pirsbd.asashina.replication.crdt.command;

import java.util.concurrent.CompletableFuture;

public record LocalRemoveCommand(String key, CompletableFuture<Void> reply) implements ReplicaCommand {
}
