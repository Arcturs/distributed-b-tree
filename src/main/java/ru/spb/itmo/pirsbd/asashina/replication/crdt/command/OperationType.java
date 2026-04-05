package ru.spb.itmo.pirsbd.asashina.replication.crdt.command;

public enum OperationType {

    PUT("put"),
    REMOVE("remove");

    private final String metricTag;

    OperationType(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }

}
