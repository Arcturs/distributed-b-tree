package ru.spb.itmo.pirsbd.asashina.replication.master.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;

@Component
public class ReplicaReplicationMetrics {

    private final MeterRegistry registry;

    public ReplicaReplicationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void bindInboxQueue(BlockingQueue<?> queue) {
        Gauge.builder("btree.replica.inbox.queue.depth", queue, BlockingQueue::size)
                .description("Current replica inbox queue depth")
                .register(registry);
    }

    public Timer.Sample startApply() {
        return Timer.start(registry);
    }

    public void stopApply(Timer.Sample sample, String operation, boolean success) {
        sample.stop(Timer.builder("btree.replica.apply.latency")
                .description("Time to apply a single replicated operation to the local B-Tree")
                .tags("operation", operation, "result", success ? "success" : "failed")
                .tag("operation", operation)
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(1),
                        Duration.ofMillis(5),
                        Duration.ofMillis(10),
                        Duration.ofMillis(25),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(250))
                .register(registry));
    }

    public void recordApplyLag(String operation, long masterReceivedAtMillis) {
        var lagMillis = Math.max(0L, System.currentTimeMillis() - masterReceivedAtMillis);
        Timer.builder("btree.replica.apply.lag")
                .description("Lag between master receiving the write and replica applying it")
                .tag("operation", operation)
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
                .record(Duration.ofMillis(lagMillis));
    }

    public void onInboxRejected() {
        Counter.builder("btree.replica.inbox.rejected.total")
                .register(registry)
                .increment();
    }

    public void onDuplicate(String operation) {
        Counter.builder("btree.replication.duplicate.total")
                .tag("operation", operation)
                .register(registry)
                .increment();
    }
}
