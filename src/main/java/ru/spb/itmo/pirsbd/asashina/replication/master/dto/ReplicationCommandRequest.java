package ru.spb.itmo.pirsbd.asashina.replication.master.dto;

import jakarta.validation.constraints.NotBlank;

public record ReplicationCommandRequest(
        @NotBlank String operationId,
        @NotBlank String operation,
        @NotBlank String key,
        String value,
        long masterReceivedAtMillis
) {
}
