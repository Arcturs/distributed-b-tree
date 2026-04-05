package ru.spb.itmo.pirsbd.asashina.replication.crdt.dto;

import jakarta.validation.constraints.NotBlank;

public record PutRequest(
        @NotBlank String key,
        @NotBlank String value
) {
}
