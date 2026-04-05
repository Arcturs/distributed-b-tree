package ru.spb.itmo.pirsbd.asashina.replication.master.dto;

import jakarta.validation.constraints.NotBlank;

public record InsertRequest(
        @NotBlank String key,
        @NotBlank String value
) {
}
