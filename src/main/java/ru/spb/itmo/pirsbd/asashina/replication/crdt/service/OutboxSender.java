package ru.spb.itmo.pirsbd.asashina.replication.crdt.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.CrdtOperation;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.CrdtOperationRequest;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.metrics.CrdtMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.properties.PeerRegistry;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "CRDT")
public class OutboxSender implements CrdtBroadcaster {

    private final BlockingQueue<CrdtOperation> queue = new LinkedBlockingQueue<>(100_000);

    private final PeerRegistry peerRegistry;
    private final RestClient restClient;
    private final CrdtMetrics metrics;
    private final ExecutorService executor;

    public OutboxSender(PeerRegistry peerRegistry, RestClient restClient, CrdtMetrics metrics) {
        this.peerRegistry = peerRegistry;
        this.restClient = restClient;
        this.metrics = metrics;
        this.executor = Executors.newSingleThreadExecutor(
                r -> Thread.ofPlatform()
                        .name("crdt-outbox-sender")
                        .unstarted(r)
        );
        this.metrics.bindOutboxQueue(queue);
    }

    @PostConstruct
    public void start() {
        executor.submit(this::loop);
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }

    @Override
    public boolean broadcast(CrdtOperation operation) {
        return queue.offer(operation);
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                var operation = queue.take();
                var request = CrdtOperationRequest.fromOperation(operation);
                for (var peer : peerRegistry.remotePeers().entrySet()) {
                    var success = sendToPeer(peer.getValue(), request);
                    metrics.onSendOutcome(peer.getKey(), operation.operationType().metricTag(), success);
                    if (!success) {
                        metrics.onSendFailureFinalized(operation.operationId(), peer.getKey(), operation.operationType().metricTag());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean sendToPeer(String peerBaseUrl, CrdtOperationRequest request) {
        try {
            restClient.post()
                    .uri(peerBaseUrl + "/internal/crdt/op")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.error("Send to peer request failed", ex);
            return false;
        }
    }
}
