package ru.spb.itmo.pirsbd.asashina.replication.master.service.worker;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.spb.itmo.pirsbd.asashina.replication.master.dictionary.ReplicaMode;
import ru.spb.itmo.pirsbd.asashina.replication.master.dictionary.OperationType;
import ru.spb.itmo.pirsbd.asashina.replication.master.dto.ReplicationAckRequest;
import ru.spb.itmo.pirsbd.asashina.replication.master.dto.ReplicationCommand;
import ru.spb.itmo.pirsbd.asashina.replication.master.metrics.ReplicaReplicationMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.master.properties.MasterReplicationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.properties.ReplicationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.master.service.BTreeStorageService;
import ru.spb.itmo.pirsbd.asashina.replication.master.service.RecentOperationStore;
import ru.spb.itmo.pirsbd.asashina.replication.master.service.ReplicaInboxService;
import ru.spb.itmo.pirsbd.asashina.replication.master.service.client.MasterAckClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "MASTER")
public class ReplicaInboxWorker {

    private final ReplicaInboxService inboxService;
    private final ReplicaReplicationMetrics metrics;
    private final BTreeStorageService storageService;
    private final MasterReplicationProperties properties;
    private final ReplicationProperties replicationProperties;
    private final MasterAckClient masterAckClient;
    private final RecentOperationStore recentOperationStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ReplicaInboxWorker(
            ReplicaInboxService inboxService,
            ReplicaReplicationMetrics metrics,
            BTreeStorageService storageService,
            MasterReplicationProperties properties,
            ReplicationProperties replicationProperties,
            MasterAckClient masterAckClient
    ) {
        this.inboxService = inboxService;
        this.metrics = metrics;
        this.storageService = storageService;
        this.properties = properties;
        this.replicationProperties = replicationProperties;
        this.masterAckClient = masterAckClient;
        this.recentOperationStore = new RecentOperationStore(replicationProperties.getDeduplicationWindow());
    }

    @PostConstruct
    public void start() {
        if (properties.getReplicaMode() != ReplicaMode.REPLICA) {
            return;
        }

        metrics.bindInboxQueue(inboxService.queue());
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    var command = inboxService.take();
                    log.info("Received command: {}", command);
                    var success = true;
                    var sample = metrics.startApply();
                    try {
                        if (!recentOperationStore.markIfNew(command.operationId())) {
                            log.info("Mark operation as duplicate: {}", command.operationId());
                            metrics.onDuplicate(command.operationType().metricTag());
                        } else if (command.operationType() == OperationType.INSERT) {
                            storageService.insert(command.key(), command.value());
                            log.info("Inserted");
                        } else {
                            storageService.remove(command.key());
                            log.info("Removed");
                        }
                        metrics.recordApplyLag(command.operationType().metricTag(), command.masterReceivedAtMillis());
                    } catch (Exception ex) {
                        log.error("Exception occurred while trying to replicate", ex);
                        success = false;
                    } finally {
                        metrics.stopApply(sample, command.operationType().metricTag(), success);
                    }

                    masterAckClient.sendAck(
                            properties.getMasterUrl(),
                            new ReplicationAckRequest(
                                    command.operationId(),
                                    replicationProperties.getNodeId(),
                                    command.operationType().metricTag(),
                                    success
                            )
                    );
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
