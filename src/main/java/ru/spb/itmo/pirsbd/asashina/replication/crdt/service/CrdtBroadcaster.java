package ru.spb.itmo.pirsbd.asashina.replication.crdt.service;

import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.CrdtOperation;

public interface CrdtBroadcaster {

    boolean broadcast(CrdtOperation operation);

}
