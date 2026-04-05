package ru.spb.itmo.pirsbd.asashina.replication.master.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.master.dictionary.ReplicaMode;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "replication.master")
public class MasterReplicationProperties {

    private ReplicaMode replicaMode = ReplicaMode.MASTER;
    private List<String> replicas = new ArrayList<>();
    private String masterUrl = "http://host.docker.internal:8080";

}
