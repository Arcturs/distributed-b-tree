package ru.spb.itmo.pirsbd.asashina.replication.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "btree")
public class BTreeProperties {

    private int degree = 3;

}
