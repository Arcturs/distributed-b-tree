package ru.spb.itmo.pirsbd.asashina.replication.master.service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import ru.spb.itmo.pirsbd.asashina.replication.master.dto.ReplicationCommand;
import ru.spb.itmo.pirsbd.asashina.replication.master.dto.ReplicationCommandRequest;

@Slf4j
@Component
public class ReplicationClient {

    private final RestClient restClient;

    public ReplicationClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public boolean sendCommand(String replicaUrl, ReplicationCommand command) {
        var request = new ReplicationCommandRequest(
                command.operationId(),
                command.operationType().metricTag(),
                command.key(),
                command.value(),
                command.masterReceivedAtMillis()
        );

        try {
            var response = restClient.post()
                    .uri(replicaUrl + "/internal/replication/command")
                    .body(request)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpServerErrorException ex) {
            log.error("Something went wrong while trying to send replication command to {}", replicaUrl, ex);
            return false;
        }
    }
}
