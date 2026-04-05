package ru.spb.itmo.pirsbd.asashina.replication.crdt.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.AckRequest;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.CrdtOperationRequest;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.metrics.CrdtMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.service.CrdtTreeService;

@RestController
@RequestMapping("/internal/crdt")
@ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "CRDT")
public class InternalCrdtController {

    private final CrdtTreeService service;
    private final CrdtMetrics metrics;

    public InternalCrdtController(CrdtTreeService service, CrdtMetrics metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @PostMapping("/op")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void receiveOperation(@RequestBody CrdtOperationRequest request) {
        service.receive(request.toOperation());
    }

    @PostMapping("/ack")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void receiveAck(@RequestBody AckRequest request) {
        metrics.onAckReceived(request);
    }

}
