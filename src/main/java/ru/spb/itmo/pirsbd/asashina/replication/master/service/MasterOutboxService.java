package ru.spb.itmo.pirsbd.asashina.replication.master.service;

import ru.spb.itmo.pirsbd.asashina.replication.master.dto.ReplicationCommand;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public record MasterOutboxService(BlockingQueue<ReplicationCommand> queue) {

    public MasterOutboxService(int queue) {
        this(new LinkedBlockingQueue<>(queue));
    }

    public boolean offer(ReplicationCommand command) {
        return queue.offer(command);
    }

    public ReplicationCommand take() throws InterruptedException {
        return queue.take();
    }

}
