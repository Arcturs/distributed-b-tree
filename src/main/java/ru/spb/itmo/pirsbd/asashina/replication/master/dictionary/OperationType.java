package ru.spb.itmo.pirsbd.asashina.replication.master.dictionary;

public enum OperationType {

    INSERT,
    REMOVE;

    public String metricTag() {
        return name().toLowerCase();
    }

    public static OperationType fromString(String value) {
        return OperationType.valueOf(value.toUpperCase());
    }

}
