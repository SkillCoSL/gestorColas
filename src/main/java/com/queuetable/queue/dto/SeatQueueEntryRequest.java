package com.queuetable.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Mesa donde sentar al cliente de la cola")
public record SeatQueueEntryRequest(
        @Schema(description = "ID de la mesa destino") @NotNull UUID tableId
) {}
