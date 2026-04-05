package ru.spb.itmo.pirsbd.asashina.replication.crdt.dto;

import java.util.List;

public record GetResult(
        String key,
        boolean exists,
        boolean conflict,
        String value,
        List<String> values
) {

    public static GetResult absent(String key) {
        return new GetResult(key, false, false, null, List.of());
    }

    public static GetResult single(String key, String value) {
        return new GetResult(key, true, false, value, List.of(value));
    }

    public static GetResult conflict(String key, List<String> values) {
        return new GetResult(key, true, true, null, List.copyOf(values));
    }

}
