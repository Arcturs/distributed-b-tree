package ru.spb.itmo.pirsbd.asashina.replication.master.service;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TreeQueryService {

    private final BTreeStorageService storageService;

    public TreeQueryService(BTreeStorageService storageService) {
        this.storageService = storageService;
    }

    public Optional<String> get(String key) {
        return storageService.get(key);
    }

    public boolean exists(String key) {
        return storageService.exists(key);
    }

    public int size() {
        return storageService.size();
    }

}
