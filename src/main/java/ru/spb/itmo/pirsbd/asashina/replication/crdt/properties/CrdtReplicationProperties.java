package ru.spb.itmo.pirsbd.asashina.replication.crdt.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
@ConfigurationProperties(prefix = "replication.crdt")
public class CrdtReplicationProperties {

    private Map<String, String> peers = new HashMap<>();

}
