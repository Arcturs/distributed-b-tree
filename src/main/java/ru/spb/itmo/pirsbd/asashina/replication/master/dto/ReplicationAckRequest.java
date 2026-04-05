package ru.spb.itmo.pirsbd.asashina.replication.master.dto;

import jakarta.validation.constraints.NotBlank;

public record ReplicationAckRequest(
        @NotBlank String operationId,
        @NotBlank String replicaId,
        @NotBlank String operation,
        boolean success
) {
}
