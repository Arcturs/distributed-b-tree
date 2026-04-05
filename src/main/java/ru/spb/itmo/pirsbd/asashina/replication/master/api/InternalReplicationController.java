package ru.spb.itmo.pirsbd.asashina.replication.master.api;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.spb.itmo.pirsbd.asashina.replication.master.dictionary.ReplicaMode;
import ru.spb.itmo.pirsbd.asashina.replication.master.dictionary.OperationType;
import ru.spb.itmo.pirsbd.asashina.replication.master.dto.ReplicationAckRequest;
import ru.spb.itmo.pirsbd.asashina.replication.master.dto.ReplicationCommandRequest;
import ru.spb.itmo.pirsbd.asashina.replication.master.metrics.MasterReplicationMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.master.metrics.ReplicaReplicationMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.master.properties.MasterReplicationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.master.service.ReplicaInboxService;
import ru.spb.itmo.pirsbd.asashina.replication.master.dto.ReplicationCommand;

@Slf4j
@RestController
@RequestMapping("/internal/replication")
@ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "MASTER")
public class InternalReplicationController {

    private final MasterReplicationProperties properties;
    private final ReplicaInboxService inboxService;
    private final ReplicaReplicationMetrics replicaMetrics;
    private final MasterReplicationMetrics masterMetrics;

    public InternalReplicationController(
            MasterReplicationProperties properties,
            ReplicaInboxService inboxService,
            ReplicaReplicationMetrics replicaMetrics,
            MasterReplicationMetrics masterMetrics
    ) {
        this.properties = properties;
        this.inboxService = inboxService;
        this.replicaMetrics = replicaMetrics;
        this.masterMetrics = masterMetrics;
    }

    @PostMapping("/command")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void replicateCommand(@Valid @RequestBody ReplicationCommandRequest request) {
        if (properties.getReplicaMode() != ReplicaMode.REPLICA) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only replicas accept internal replication commands");
        }

        var command = new ReplicationCommand(
                request.operationId(),
                OperationType.fromString(request.operation()),
                request.key(),
                request.value(),
                request.masterReceivedAtMillis()
        );
        if (!inboxService.offer(command)) {
            replicaMetrics.onInboxRejected();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Replica inbox queue is full");
        }
    }

    @PostMapping("/acks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void receiveAck(
            @Valid @RequestBody ReplicationAckRequest request
    ) {
        log.info("Received ack command {}", request.operationId());
        if (properties.getReplicaMode() != ReplicaMode.MASTER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the master accepts replica acknowledgements");
        }

        masterMetrics.onAck(request.replicaId(), request.operation(), request.success());
        masterMetrics.markReplicaCompleted(
                request.operationId(),
                request.success(),
                request.success() ? "ack" : "failed"
        );
    }

}
