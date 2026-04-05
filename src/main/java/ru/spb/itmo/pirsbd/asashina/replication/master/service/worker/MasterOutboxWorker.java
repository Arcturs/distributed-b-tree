package ru.spb.itmo.pirsbd.asashina.replication.master.service.worker;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.spb.itmo.pirsbd.asashina.replication.master.dictionary.ReplicaMode;
import ru.spb.itmo.pirsbd.asashina.replication.master.dto.ReplicationCommand;
import ru.spb.itmo.pirsbd.asashina.replication.master.metrics.MasterReplicationMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.master.properties.MasterReplicationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.properties.ReplicationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.master.service.MasterOutboxService;
import ru.spb.itmo.pirsbd.asashina.replication.master.service.client.ReplicationClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "MASTER")
public class MasterOutboxWorker {

    private final MasterOutboxService outboxService;
    private final ReplicationClient replicationClient;
    private final MasterReplicationProperties properties;
    private final MasterReplicationMetrics metrics;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MasterOutboxWorker(
            MasterOutboxService outboxService,
            ReplicationClient replicationClient,
            MasterReplicationProperties properties,
            MasterReplicationMetrics metrics
    ) {
        this.outboxService = outboxService;
        this.replicationClient = replicationClient;
        this.properties = properties;
        this.metrics = metrics;
    }

    @PostConstruct
    public void start() {
        if (properties.getReplicaMode() != ReplicaMode.MASTER) {
            return;
        }

        metrics.bindOutboxQueue(outboxService.queue());
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    var command = outboxService.take();
                    var replicas = properties.getReplicas();
                    for (var replicaUrl : replicas) {
                        var sent = replicationClient.sendCommand(replicaUrl, command);
                        metrics.onSendResult(replicaUrl, command.operationType().metricTag(), sent);
                        if (!sent) {
                            metrics.markReplicaCompleted(command.operationId(), false, "send_failed");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }

}
