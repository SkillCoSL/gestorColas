package com.queuetable.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Schema(description = "Campos a actualizar de la reserva (todos opcionales)")
public record UpdateReservationRequest(
        @Schema(example = "Ana Martinez") @Size(max = 200) String customerName,
        @Schema(example = "+34 611 222 333") @Size(max = 50) String customerPhone,
        @Schema(example = "6") @Min(1) Integer partySize,
        @Schema(description = "Nueva fecha y hora (ISO 8601)") Instant reservedAt,
        @Schema(example = "Cumpleanios, poner vela") String notes
) {}
