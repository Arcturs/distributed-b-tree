package ru.spb.itmo.pirsbd.asashina.replication.master.api;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.spb.itmo.pirsbd.asashina.replication.master.dto.*;
import ru.spb.itmo.pirsbd.asashina.replication.master.service.MasterCommandService;
import ru.spb.itmo.pirsbd.asashina.replication.master.service.TreeQueryService;

@RestController
@AllArgsConstructor
@RequestMapping("/api/tree")
@ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "MASTER")
public class TreeController {

    private final MasterCommandService commandService;
    private final TreeQueryService queryService;

    @PostMapping("/insert")
    @ResponseStatus(HttpStatus.OK)
    public void insert(@Valid @RequestBody InsertRequest request) {
        commandService.insert(request.key(), request.value());
    }

    @DeleteMapping("/{key}")
    public RemoveResponse remove(@PathVariable String key) {
        return new RemoveResponse(commandService.remove(key));
    }

    @GetMapping("/{key}")
    public ValueResponse get(@PathVariable String key) {
        return queryService.get(key)
                .map(value -> new ValueResponse(key, value))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Key not found: " + key));
    }

    @GetMapping("/{key}/exists")
    public ExistsResponse exists(@PathVariable String key) {
        return new ExistsResponse(key, queryService.exists(key));
    }

}
