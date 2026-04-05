package ru.spb.itmo.pirsbd.asashina.replication.crdt.service;

import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.AckRequest;

public interface AckSender {

    boolean sendAck(String originReplicaId, AckRequest request);

}
