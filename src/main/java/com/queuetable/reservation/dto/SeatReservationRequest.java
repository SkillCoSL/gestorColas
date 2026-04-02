package com.queuetable.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Mesa donde sentar la reserva")
public record SeatReservationRequest(
        @Schema(description = "ID de la mesa destino") @NotNull UUID tableId
) {}
