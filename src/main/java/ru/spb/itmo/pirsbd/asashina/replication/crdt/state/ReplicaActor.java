package ru.spb.itmo.pirsbd.asashina.replication.crdt.state;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.*;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.AckRequest;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.GetResult;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.metrics.CrdtMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.properties.PeerRegistry;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.service.AckSender;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.service.CrdtBroadcaster;
import ru.spb.itmo.pirsbd.asashina.tree.BTree;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class ReplicaActor implements AutoCloseable {

    private final String replicaId;
    private final BTree<String, KeyState<String>> tree;
    private final PeerRegistry peerRegistry;
    private final CrdtBroadcaster broadcaster;
    private final AckSender ackSender;
    private final CrdtMetrics metrics;
    private final ReadSnapshot snapshot;

    private final BlockingQueue<ReplicaCommand> mailbox = new LinkedBlockingQueue<>(100_000);
    private final ExecutorService executor;

    private final Map<String, Long> delivered = new HashMap<>();
    private final Set<String> seenOperationIds = new HashSet<>();
    private final Map<String, CrdtOperation> pending = new LinkedHashMap<>();

    public ReplicaActor(
            String replicaId,
            BTree<String, KeyState<String>> tree,
            PeerRegistry peerRegistry,
            CrdtBroadcaster broadcaster,
            AckSender ackSender,
            CrdtMetrics metrics
    ) {
        this.replicaId = replicaId;
        this.tree = tree;
        this.peerRegistry = peerRegistry;
        this.broadcaster = broadcaster;
        this.ackSender = ackSender;
        this.metrics = metrics;
        this.executor = Executors.newSingleThreadExecutor(
                r -> Thread.ofPlatform()
                        .name("replica-actor-" + replicaId)
                        .unstarted(r)
        );
        this.snapshot = new ReadSnapshot(metrics);
        this.metrics.bindInboxQueue(mailbox);
    }

    @PostConstruct
    public void start() {
        executor.submit(this::eventLoop);
    }

    @PreDestroy
    @Override
    public void close() {
        executor.shutdownNow();
    }

    public void localPut(String key, String value) {
        CompletableFuture<Void> reply = new CompletableFuture<>();
        var accepted = mailbox.offer(new LocalPutCommand(key, value, reply));
        if (!accepted) {
            metrics.onInboxRejected();
            throw new IllegalStateException("Replica actor mailbox is full");
        }
        reply.join();
    }

    public void localRemove(String key) {
        CompletableFuture<Void> reply = new CompletableFuture<>();
        var accepted = mailbox.offer(new LocalRemoveCommand(key, reply));
        if (!accepted) {
            metrics.onInboxRejected();
            throw new IllegalStateException("Replica actor mailbox is full");
        }
        reply.join();
    }

    public void receiveRemote(CrdtOperation operation) {
        var accepted = mailbox.offer(new RemoteOperationCommand(operation));
        if (!accepted) {
            metrics.onInboxRejected();
            throw new IllegalStateException("Replica actor mailbox is full");
        }
    }

    public GetResult get(String key) {
        return snapshot.get(key);
    }

    public boolean exists(String key) {
        return snapshot.exists(key);
    }

    private void eventLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                var command = mailbox.take();
                switch (command) {
                    case LocalPutCommand put -> handleLocalPut(put);
                    case LocalRemoveCommand remove -> handleLocalRemove(remove);
                    case RemoteOperationCommand remote -> handleRemote(remote.operation());
                }
                drainPending();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.error("Something went wrong during command executing", ex);
            }
        }
    }

    private void handleLocalPut(LocalPutCommand command) {
        var nextCounter = delivered.getOrDefault(replicaId, 0L) + 1;
        delivered.put(replicaId, nextCounter);
        var operation = new PutOperation(
                UUID.randomUUID().toString(),
                replicaId,
                nextCounter,
                new VersionVector(new HashMap<>(delivered)),
                command.key(),
                System.currentTimeMillis(),
                command.value()
        );
        applyToLocalState(operation);
        metrics.registerLocalReplication(
                operation.operationId(),
                operation.operationType().metricTag(),
                peerRegistry.remotePeerCount()
        );
        var enqueued = broadcaster.broadcast(operation);
        if (!enqueued) {
            metrics.onOutboxRejected();
            for (String peerId : peerRegistry.remotePeerIds()) {
                metrics.onSendFailureFinalized(operation.operationId(), peerId, operation.operationType().metricTag());
            }
        }
        command.reply().complete(null);
    }

    private void handleLocalRemove(LocalRemoveCommand command) {
        var nextCounter = delivered.getOrDefault(replicaId, 0L) + 1;
        delivered.put(replicaId, nextCounter);
        var operation = new RemoveOperation(
                UUID.randomUUID().toString(),
                replicaId,
                nextCounter,
                new VersionVector(new HashMap<>(delivered)),
                command.key(),
                System.currentTimeMillis()
        );
        applyToLocalState(operation);
        metrics.registerLocalReplication(
                operation.operationId(),
                operation.operationType().metricTag(),
                peerRegistry.remotePeerCount()
        );
        var enqueued = broadcaster.broadcast(operation);
        if (!enqueued) {
            metrics.onOutboxRejected();
            for (String peerId : peerRegistry.remotePeerIds()) {
                metrics.onSendFailureFinalized(operation.operationId(), peerId, operation.operationType().metricTag());
            }
        }
        command.reply().complete(null);
    }

    private void handleRemote(CrdtOperation operation) {
        metrics.onCommandReceived(operation.operationType().metricTag());

        if (seenOperationIds.contains(operation.operationId())) {
            metrics.onDuplicateOperation(operation.operationType().metricTag());
            sendAck(operation, true);
            return;
        }

        if (pending.containsKey(operation.operationId())) {
            metrics.onDuplicateOperation(operation.operationType().metricTag());
            return;
        }

        if (causallyReady(operation)) {
            applyRemote(operation);
        } else {
            pending.put(operation.operationId(), operation);
            metrics.incrementPendingDepth();
            metrics.onCausallyBlocked(operation.operationType().metricTag());
        }
    }

    private boolean causallyReady(CrdtOperation operation) {
        var sender = operation.originReplica();
        var deliveredFromSender = delivered.getOrDefault(sender, 0L);
        if (deliveredFromSender != operation.originCounter() - 1) {
            return false;
        }

        for (var entry : operation.versionVector().entries().entrySet()) {
            var replica = entry.getKey();
            var required = entry.getValue();
            if (replica.equals(sender)) {
                continue;
            }
            var local = delivered.getOrDefault(replica, 0L);
            if (local < required) {
                return false;
            }
        }

        return true;
    }

    private void drainPending() {
        boolean progressed;
        do {
            progressed = false;
            var it = pending.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (!causallyReady(entry.getValue())) {
                    continue;
                }
                CrdtOperation operation = entry.getValue();
                it.remove();
                metrics.decrementPendingDepth();
                applyRemote(operation);
                progressed = true;
            }
        } while (progressed);
    }

    private void applyRemote(CrdtOperation operation) {
        var sample = metrics.startRemoteApply();
        var success = false;
        try {
            applyToLocalState(operation);
            metrics.recordRemoteApplyLag(operation.operationType().metricTag(), operation.originAcceptedAtMillis());
            success = true;
        } finally {
            metrics.stopRemoteApply(sample, operation.operationType().metricTag(), success);
            sendAck(operation, success);
        }
    }

    private void sendAck(CrdtOperation operation, boolean success) {
        var ackSent = ackSender.sendAck(
                operation.originReplica(),
                new AckRequest(
                        operation.operationId(),
                        operation.operationType().metricTag(),
                        replicaId,
                        success
                )
        );
        metrics.onAckSendOutcome(operation.originReplica(), operation.operationType().metricTag(), ackSent);
    }

    private void applyToLocalState(CrdtOperation operation) {
        if (seenOperationIds.contains(operation.operationId())) {
            return;
        }

        var current = tree.select(operation.key());
        if (current == null) {
            current = new KeyState<>();
        }
        VersionedValue<String> incoming = switch (operation) {
            case PutOperation put -> new VersionedValue<>(
                    put.operationId(),
                    put.originReplica(),
                    put.originCounter(),
                    put.versionVector(),
                    put.value(),
                    false
            );
            case RemoveOperation remove -> new VersionedValue<>(
                    remove.operationId(),
                    remove.originReplica(),
                    remove.originCounter(),
                    remove.versionVector(),
                    null,
                    true
            );
        };

        var next = current.apply(incoming);
        tree.insert(operation.key(), next);
        delivered.put(operation.originReplica(), operation.originCounter());
        seenOperationIds.add(operation.operationId());
        snapshot.put(operation.key(), toGetResult(operation.key(), next));
    }

    private GetResult toGetResult(String key, KeyState<String> state) {
        var visible = state.visibleValues();
        if (visible.isEmpty()) {
            return GetResult.absent(key);
        }

        if (visible.size() == 1) {
            return GetResult.single(key, visible.getFirst());
        }

        return GetResult.conflict(key, visible);
    }
}
