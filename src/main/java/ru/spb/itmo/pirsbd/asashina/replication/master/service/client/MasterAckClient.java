package ru.spb.itmo.pirsbd.asashina.replication.master.service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import ru.spb.itmo.pirsbd.asashina.replication.master.dto.ReplicationAckRequest;

@Slf4j
@Component
public class MasterAckClient {

    private final RestClient restClient;

    public MasterAckClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public boolean sendAck(String masterUrl, ReplicationAckRequest request) {
        try {
            restClient.post()
                    .uri(masterUrl + "/internal/replication/acks")
                    .body(request)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity()
                    .getBody();
            return true;
        } catch (HttpServerErrorException ex) {
            log.error("Something wrong happened while sending ack", ex);
            return false;
        }
    }

}
