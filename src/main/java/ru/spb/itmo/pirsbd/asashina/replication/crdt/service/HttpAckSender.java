package ru.spb.itmo.pirsbd.asashina.replication.crdt.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.AckRequest;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.properties.PeerRegistry;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "CRDT")
public class HttpAckSender implements AckSender {

    private final RestClient restClient;
    private final PeerRegistry peerRegistry;

    public HttpAckSender(RestClient restClient, PeerRegistry peerRegistry) {
        this.restClient = restClient;
        this.peerRegistry = peerRegistry;
    }

    @Override
    public boolean sendAck(String originReplicaId, AckRequest request) {
        return peerRegistry.urlFor(originReplicaId)
                .map(url -> doSend(url, request))
                .orElse(false);
    }

    private boolean doSend(String baseUrl, AckRequest request) {
        try {
            restClient.post()
                    .uri(baseUrl + "/internal/crdt/ack")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.error("Send ack request failed", ex);
            return false;
        }
    }
}
