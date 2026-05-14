package ru.spb.itmo.pirsbd.asashina.replication.master.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MasterReplicationMetrics {

    private final MeterRegistry registry;
    private final Map<String, PendingReplication> pending = new ConcurrentHashMap<>();
    private final AtomicInteger inflight = new AtomicInteger();

    public MasterReplicationMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("btree.replication.inflight", inflight, AtomicInteger::get)
                .description("Number of write operations still waiting for replica completion")
                .register(registry);
        Gauge.builder(
                    "btree.replication.oldest.inflight.seconds",
                        this,
                        MasterReplicationMetrics::oldestInflightSeconds)
                .description("Age of the oldest inflight replication in seconds")
                .register(registry);
    }

    public void bindOutboxQueue(BlockingQueue<?> queue) {
        Gauge.builder("btree.replication.outbox.queue.depth", queue, BlockingQueue::size)
                .description("Current master outbox queue depth")
                .register(registry);
    }

    public void start(String operationId, String operation, int expectedReplicas, Duration ackTimeout) {
        pending.put(
                operationId,
                new PendingReplication(operation, expectedReplicas, System.nanoTime(), ackTimeout.toNanos())
        );
        inflight.incrementAndGet();
    }

    public void completeImmediatelyIfNoReplicas(String operationId) {
        var pendingReplication = pending.get(operationId);
        if (pendingReplication != null && pendingReplication.expectedReplicas == 0) {
            finish(pendingReplication, operationId, "success");
        }
    }


    public void abort(String operationId, String result) {
        var operation = pending.get(operationId);
        if (operation != null) {
            finish(operation, operationId, result);
        }
    }

    public void onSendResult(String replica, String operation, boolean success) {
        Counter.builder("btree.replication.send.total")
                .tags("replica", replica, "operation", operation, "result", success ? "success" : "failed")
                .register(registry)
                .increment();
    }

    public void onAck(String replica, String operation, boolean success) {
        Counter.builder("btree.replication.ack.total")
                .tags("replica", replica, "operation", operation, "result", success ? "success" : "failed")
                .register(registry)
                .increment();
    }

    public void onOutboxRejected() {
        Counter.builder("btree.replication.outbox.rejected.total")
                .register(registry)
                .increment();
    }

    public void markReplicaCompleted(String operationId, boolean success, String resultHint) {
        var operation = pending.get(operationId);
        if (operation == null || operation.completed.get()) {
            return;
        }

        if (!success && "timeout".equals(resultHint)) {
            operation.result = "timeout";
        } else if (!success && !"timeout".equals(operation.result)) {
            operation.result = "failed";
        }
        if (operation.remaining.decrementAndGet() <= 0) {
            finish(operation, operationId, operation.result);
        }
    }

    public void expireTimedOut() {
        var now = System.nanoTime();
        for (var entry : pending.entrySet()) {
            var operation = entry.getValue();
            if (!operation.completed.get() && now - operation.startedAtNanos >= operation.ackTimeoutNanos) {
                Counter.builder("btree.replication.timeout.total")
                        .tag("operation", operation.operation)
                        .register(registry)
                        .increment();
                finish(operation, entry.getKey(), "timeout");
            }
        }
    }

    private void finish(PendingReplication replication, String operationId, String result) {
        if (!replication.completed.compareAndSet(false, true)) {
            return;
        }

        pending.remove(operationId);
        inflight.decrementAndGet();
        var durationNanos = Math.max(0L, System.nanoTime() - replication.startedAtNanos);
        Timer.builder("btree.replication.e2e.latency")
                .description("Time from master accepting a write to completion on all replicas")
                .tags("operation", replication.operation, "result", result)
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(10),
                        Duration.ofMillis(25),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(250),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5))
                .register(registry)
                .record(Duration.ofNanos(durationNanos));
    }

    private double oldestInflightSeconds() {
        var now = System.nanoTime();
        return pending.values().stream()
                .filter(p -> !p.completed.get())
                .mapToDouble(p -> (now - p.startedAtNanos) / 1_000_000_000.0)
                .max()
                .orElse(0.0);
    }

    private static final class PendingReplication {

        private final String operation;
        private final int expectedReplicas;
        private final AtomicInteger remaining;
        private final long startedAtNanos;
        private final long ackTimeoutNanos;
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private volatile String result = "success";

        private PendingReplication(String operation, int expectedReplicas, long startedAtNanos, long ackTimeoutNanos) {
            this.operation = operation;
            this.expectedReplicas = expectedReplicas;
            this.remaining = new AtomicInteger(expectedReplicas);
            this.startedAtNanos = startedAtNanos;
            this.ackTimeoutNanos = ackTimeoutNanos;
        }

    }

}
