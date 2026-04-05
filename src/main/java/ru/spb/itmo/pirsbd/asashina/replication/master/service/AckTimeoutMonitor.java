package ru.spb.itmo.pirsbd.asashina.replication.master.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.spb.itmo.pirsbd.asashina.replication.master.dictionary.ReplicaMode;
import ru.spb.itmo.pirsbd.asashina.replication.master.metrics.MasterReplicationMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.master.properties.MasterReplicationProperties;

@Async
@Component
public class AckTimeoutMonitor {

    private final MasterReplicationProperties properties;
    private final MasterReplicationMetrics metrics;

    public AckTimeoutMonitor(MasterReplicationProperties properties, MasterReplicationMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelay = 1000)
    public void checkTimeouts() {
        if (properties.getReplicaMode() == ReplicaMode.MASTER) {
            metrics.expireTimedOut();
        }
    }

}
