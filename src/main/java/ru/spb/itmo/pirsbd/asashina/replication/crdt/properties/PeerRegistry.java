package ru.spb.itmo.pirsbd.asashina.replication.crdt.properties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PeerRegistry {

    private final String selfId;
    private final Map<String, String> peers;

    public PeerRegistry(String selfId, Map<String, String> peers) {
        this.selfId = selfId;
        this.peers = Map.copyOf(peers);
    }

    public Map<String, String> remotePeers() {
        Map<String, String> filtered = new LinkedHashMap<>();
        peers.forEach((id, url) -> {
            if (!id.equals(selfId)) {
                filtered.put(id, url);
            }
        });
        return filtered;
    }

    public Set<String> remotePeerIds() {
        return remotePeers().keySet();
    }

    public Optional<String> urlFor(String replicaId) {
        return Optional.ofNullable(peers.get(replicaId));
    }

    public int remotePeerCount() {
        return remotePeers().size();
    }

}
