package ru.spb.itmo.pirsbd.asashina;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.properties.CrdtReplicationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.master.properties.MasterReplicationProperties;
import ru.spb.itmo.pirsbd.asashina.replication.properties.BTreeProperties;
import ru.spb.itmo.pirsbd.asashina.replication.properties.ReplicationProperties;

@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({
        BTreeProperties.class,
        ReplicationProperties.class,
        MasterReplicationProperties.class,
        CrdtReplicationProperties.class
})
public class TreeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TreeApplication.class, args);
    }

}
