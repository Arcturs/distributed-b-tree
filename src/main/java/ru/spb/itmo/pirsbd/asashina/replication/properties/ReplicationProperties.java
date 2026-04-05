package ru.spb.itmo.pirsbd.asashina.replication.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.dictionary.ReplicationMode;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "replication")
public class ReplicationProperties {

    private ReplicationMode mode = ReplicationMode.MASTER;
    private String nodeId;
    private int outboxCapacity = 10_000;
    private int inboxCapacity = 10_000;
    private int deduplicationWindow = 100_000;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    private Duration ackTimeout = Duration.ofSeconds(10);

}
