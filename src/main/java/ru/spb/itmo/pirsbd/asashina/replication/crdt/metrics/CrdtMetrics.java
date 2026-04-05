package ru.spb.itmo.pirsbd.asashina.replication.crdt.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.AckRequest;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

@Component
public class CrdtMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger inflight = new AtomicInteger();
    private final ConcurrentHashMap<String, PendingReplication> pending = new ConcurrentHashMap<>();

    private final Counter conflictKeysCounter;
    private final Counter pendingDepthCounter;

    public CrdtMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("crdt.replication.inflight", inflight, AtomicInteger::get)
                .description("Local writes still waiting for remote replication outcomes")
                .register(registry);
        Gauge.builder("crdt.replication.oldest.inflight.seconds", this, CrdtMetrics::oldestInflightSeconds)
                .baseUnit("seconds")
                .description("Age in seconds of the oldest inflight local replication")
                .register(registry);
        this.conflictKeysCounter = registry.counter("crdt.conflict.keys");
        this.pendingDepthCounter = registry.counter("crdt.causal.pending.depth");
    }

    public void bindInboxQueue(BlockingQueue<?> queue) {
        Gauge.builder("crdt.replica.inbox.queue.depth", queue, BlockingQueue::size)
                .description("Replica actor mailbox depth")
                .register(registry);
    }

    public void bindOutboxQueue(BlockingQueue<?> queue) {
        Gauge.builder("crdt.replication.outbox.queue.depth", queue, BlockingQueue::size)
                .description("Replication sender queue depth")
                .register(registry);
    }

    public void incrementPendingDepth() {
        pendingDepthCounter.increment();
    }

    public void decrementPendingDepth() {
        pendingDepthCounter.increment(-1);
    }

    public void incrementConflictKeys() {
        conflictKeysCounter.increment();
    }

    public void decrementConflictKeys() {
        conflictKeysCounter.increment(-1);
    }

    public void registerLocalReplication(String operationId, String operation, int expectedRemoteAcks) {
        if (expectedRemoteAcks <= 0) {
            e2eTimer(operation, "success").record(Duration.ZERO);
            return;
        }
        var pendingReplication = new PendingReplication(
                Timer.start(registry),
                expectedRemoteAcks,
                System.nanoTime()
        );
        pending.put(operationId, pendingReplication);
        inflight.incrementAndGet();
    }

    public void onAckReceived(AckRequest request) {
        counter(
                    "crdt.replication.ack",
                    "from_replica", request.fromReplica(),
                    "operation", request.operation(),
                    "result", request.success() ? "success" : "failed")
                .increment();
        markPeerFinished(request.operationId(), request.fromReplica(), request.operation(), request.success());
    }

    public void onSendOutcome(String peerId, String operation, boolean success) {
        counter(
                    "crdt.replication.send",
                    "peer", peerId,
                    "operation", operation,
                    "result", success ? "success" : "failed")
                .increment();
    }

    public void onAckSendOutcome(String originReplica, String operation, boolean success) {
        counter(
                    "crdt.replication.ack.send",
                    "origin_replica", originReplica,
                    "operation", operation,
                    "result", success ? "success" : "failed")
                .increment();
    }

    public void onSendFailureFinalized(String operationId, String peerId, String operation) {
        markPeerFinished(operationId, peerId, operation, false);
    }

    public Timer.Sample startRemoteApply() {
        return Timer.start(registry);
    }

    public void stopRemoteApply(Timer.Sample sample, String operation, boolean success) {
        sample.stop(Timer.builder("crdt.replica.apply.latency")
                .description("Latency of applying a remote operation to the local MV-register B-Tree")
                .tags("operation", operation, "result", success ? "success" : "failed")
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(1),
                        Duration.ofMillis(2),
                        Duration.ofMillis(5),
                        Duration.ofMillis(10),
                        Duration.ofMillis(25),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(250))
                .register(registry));
    }

    public void recordRemoteApplyLag(String operation, long originAcceptedAtMillis) {
        var lagMillis = Math.max(0, System.currentTimeMillis() - originAcceptedAtMillis);
        applyLagTimer(operation).record(Duration.ofMillis(lagMillis));
    }

    public void onCommandReceived(String operation) {
        counter("crdt.replica.command.received", "operation", operation).increment();
    }

    public void onDuplicateOperation(String operation) {
        counter("crdt.replication.duplicate", "operation", operation).increment();
    }

    public void onCausallyBlocked(String operation) {
        counter("crdt.causally.blocked", "operation", operation).increment();
    }

    public void onInboxRejected() {
        counter("crdt.replica.inbox.rejected").increment();
    }

    public void onOutboxRejected() {
        counter("crdt.replication.outbox.rejected").increment();
    }

    private void markPeerFinished(String operationId, String peerId, String operation, boolean success) {
        var state = pending.get(operationId);
        if (state == null) {
            return;
        }

        if (!state.completedPeers.add(peerId)) {
            return;
        }

        if (!success) {
            state.failed.set(true);
        }
        if (state.remaining.decrementAndGet() == 0) {
            pending.remove(operationId);
            inflight.decrementAndGet();
            state.sample.stop(e2eTimer(operation, state.failed.get() ? "failed" : "success"));
        }
    }


    private Timer e2eTimer(String operation, String result) {
        return Timer.builder("crdt.replication.e2e.latency")
                .description("Time from local write acceptance to final remote replication outcome")
                .tags("operation", operation, "result", result)
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(5),
                        Duration.ofMillis(10),
                        Duration.ofMillis(25),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(250),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5))
                .register(registry);
    }

    private Timer applyLagTimer(String operation) {
        return Timer.builder("crdt.replica.apply.lag")
                .description("Lag between origin acceptance time and remote apply time")
                .tag("operation", operation)
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(5),
                        Duration.ofMillis(10),
                        Duration.ofMillis(25),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(250),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5))
                .register(registry);
    }

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }

    private double oldestInflightSeconds() {
        long now = System.nanoTime();
        return pending.values().stream()
                .mapToDouble(p -> (now - p.startedAtNanos) / 1_000_000_000.0)
                .max()
                .orElse(0.0);
    }

    private static final class PendingReplication {
        private final Timer.Sample sample;
        private final AtomicInteger remaining;
        private final long startedAtNanos;
        private final Set<String> completedPeers = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean failed = new AtomicBoolean(false);

        private PendingReplication(Timer.Sample sample, int expected, long startedAtNanos) {
            this.sample = sample;
            this.remaining = new AtomicInteger(expected);
            this.startedAtNanos = startedAtNanos;
        }
    }
}
