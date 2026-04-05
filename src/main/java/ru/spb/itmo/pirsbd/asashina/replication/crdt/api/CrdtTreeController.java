package ru.spb.itmo.pirsbd.asashina.replication.crdt.api;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.ExistsResponse;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.GetResult;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.PutRequest;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.service.CrdtTreeService;

@RestController
@RequestMapping("/api/crdt/tree")
@ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "CRDT")
public class CrdtTreeController {

    private final CrdtTreeService service;

    public CrdtTreeController(CrdtTreeService service) {
        this.service = service;
    }

    @PostMapping("/insert")
    public void insert(@Valid @RequestBody PutRequest request) {
        service.put(request.key(), request.value());
    }

    @DeleteMapping("/{key}")
    public void remove(@PathVariable String key) {
        service.remove(key);
    }

    @GetMapping("/{key}")
    public GetResult get(@PathVariable String key) {
        return service.get(key);
    }

    @GetMapping("/{key}/exists")
    public ExistsResponse exists(@PathVariable String key) {
        return new ExistsResponse(key, service.exists(key));
    }
}
