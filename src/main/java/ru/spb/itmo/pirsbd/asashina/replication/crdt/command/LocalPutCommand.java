package ru.spb.itmo.pirsbd.asashina.replication.crdt.command;

import java.util.concurrent.CompletableFuture;

public record LocalPutCommand(String key, String value, CompletableFuture<Void> reply) implements ReplicaCommand {
}
