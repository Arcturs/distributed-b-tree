package ru.spb.itmo.pirsbd.asashina.replication.master.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.spb.itmo.pirsbd.asashina.replication.master.dictionary.ReplicaMode;
import ru.spb.itmo.pirsbd.asashina.replication.master.dictionary.OperationType;
import ru.spb.itmo.pirsbd.asashina.replication.master.dto.ReplicationCommand;
import ru.spb.itmo.pirsbd.asashina.replication.master.metrics.MasterReplicationMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.master.properties.MasterReplicationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.properties.ReplicationProperties;

import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "MASTER")
public class MasterCommandService {

    private final BTreeStorageService storageService;
    private final MasterReplicationProperties properties;
    private final ReplicationProperties replicationProperties;
    private final MasterOutboxService outboxService;
    private final MasterReplicationMetrics metrics;

    public MasterCommandService(
            BTreeStorageService storageService,
            MasterReplicationProperties properties,
            ReplicationProperties replicationProperties,
            MasterOutboxService outboxService,
            MasterReplicationMetrics metrics
    ) {
        this.storageService = storageService;
        this.properties = properties;
        this.replicationProperties = replicationProperties;
        this.outboxService = outboxService;
        this.metrics = metrics;
    }

    public void insert(String key, String value) {
        ensureMaster();
        var operationId = UUID.randomUUID().toString();
        var receivedAt = System.currentTimeMillis();
        metrics.start(
                operationId,
                OperationType.INSERT.metricTag(),
                properties.getReplicas().size(),
                replicationProperties.getAckTimeout()
        );

        var command = new ReplicationCommand(operationId, OperationType.INSERT, key, value, receivedAt);
        if (!outboxService.offer(command)) {
            metrics.onOutboxRejected();
            metrics.abort(operationId, "rejected");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Master outbox queue is full");
        }
        storageService.insert(key, value);
        metrics.completeImmediatelyIfNoReplicas(operationId);
    }

    public boolean remove(String key) {
        ensureMaster();
        if (outboxService.queue().remainingCapacity() == 0) {
            metrics.onOutboxRejected();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Master outbox queue is full");
        }

        var removed = storageService.remove(key);
        if (!removed) {
            return false;
        }

        var operationId = UUID.randomUUID().toString();
        var receivedAt = System.currentTimeMillis();
        metrics.start(
                operationId,
                OperationType.REMOVE.metricTag(),
                properties.getReplicas().size(),
                replicationProperties.getAckTimeout());

        var command = new ReplicationCommand(operationId, OperationType.REMOVE, key, null, receivedAt);
        if (!outboxService.offer(command)) {
            metrics.onOutboxRejected();
            metrics.abort(operationId, "rejected");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Master outbox queue is full after local remove; restart to reconcile replicas"
            );
        }

        metrics.completeImmediatelyIfNoReplicas(operationId);
        return true;
    }

    private void ensureMaster() {
        if (properties.getReplicaMode() != ReplicaMode.MASTER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the master accepts write operations");
        }
    }
}
