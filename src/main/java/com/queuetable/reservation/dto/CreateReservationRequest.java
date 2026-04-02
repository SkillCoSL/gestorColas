package com.queuetable.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Schema(description = "Datos para crear una nueva reserva")
public record CreateReservationRequest(
        @Schema(example = "Ana Martinez") @NotBlank @Size(max = 200) String customerName,
        @Schema(example = "+34 611 222 333") @Size(max = 50) String customerPhone,
        @Schema(description = "Tamanio del grupo", example = "4") @Min(1) int partySize,
        @Schema(description = "Fecha y hora de la reserva (ISO 8601)") @NotNull Instant reservedAt,
        @Schema(example = "Mesa en terraza si es posible") String notes
) {}
