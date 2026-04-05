package ru.spb.itmo.pirsbd.asashina.replication.crdt.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.CrdtOperation;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.GetResult;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.ReplicaActor;

@Service
@ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "CRDT")
public class CrdtTreeService {

    private final ReplicaActor actor;

    public CrdtTreeService(ReplicaActor actor) {
        this.actor = actor;
    }

    public void put(String key, String value) {
        actor.localPut(key, value);
    }

    public void remove(String key) {
        actor.localRemove(key);
    }

    public void receive(CrdtOperation operation) {
        actor.receiveRemote(operation);
    }

    public GetResult get(String key) {
        return actor.get(key);
    }

    public boolean exists(String key) {
        return actor.exists(key);
    }

}
