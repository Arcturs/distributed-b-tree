package ru.spb.itmo.pirsbd.asashina.replication.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.metrics.CrdtMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.properties.CrdtReplicationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.properties.PeerRegistry;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.service.AckSender;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.service.CrdtBroadcaster;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.ReplicaActor;
import ru.spb.itmo.pirsbd.asashina.replication.properties.BTreeProperties;
import ru.spb.itmo.pirsbd.asashina.replication.properties.ReplicationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.master.service.MasterOutboxService;
import ru.spb.itmo.pirsbd.asashina.replication.master.service.ReplicaInboxService;
import ru.spb.itmo.pirsbd.asashina.tree.BTree;

@Configuration
public class AppConfig {

    @Bean
    public BTree<String, String> bTree(BTreeProperties properties) {
        return new BTree<>(properties.getDegree());
    }

    @Bean
    public RestClient restClient(RestTemplateBuilder builder, ReplicationProperties properties) {
        return RestClient.create(
                builder.connectTimeout(properties.getConnectTimeout())
                        .readTimeout(properties.getReadTimeout())
                        .build()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "MASTER")
    public MasterOutboxService masterOutboxService(ReplicationProperties properties) {
        return new MasterOutboxService(properties.getOutboxCapacity());
    }

    @Bean
    @ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "MASTER")
    public ReplicaInboxService replicaInboxService(ReplicationProperties properties) {
        return new ReplicaInboxService(properties.getInboxCapacity());
    }

    @Bean
    @ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "CRDT")
    public PeerRegistry peerRegistry(
            ReplicationProperties replicationProperties,
            CrdtReplicationProperties properties
    ) {
        return new PeerRegistry(replicationProperties.getNodeId(), properties.getPeers());
    }

    @Bean
    @ConditionalOnProperty(prefix = "replication", name = "mode", havingValue = "CRDT")
    public ReplicaActor replicaActor(
            ReplicationProperties replicationProperties,
            BTreeProperties btreeProperties,
            PeerRegistry peerRegistry,
            CrdtBroadcaster broadcaster,
            AckSender ackSender,
            CrdtMetrics metrics
    ) {
        return new ReplicaActor(
                replicationProperties.getNodeId(),
                new BTree<>(btreeProperties.getDegree()),
                peerRegistry,
                broadcaster,
                ackSender,
                metrics
        );
    }

}
