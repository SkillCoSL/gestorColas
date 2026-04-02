package com.queuetable.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para unirse a la cola")
public record JoinQueueRequest(
        @Schema(description = "Nombre del cliente", example = "Maria Lopez") @NotBlank @Size(max = 200) String customerName,
        @Schema(description = "Tamanio del grupo", example = "3") @Min(1) int partySize,
        @Schema(description = "Telefono (opcional)", example = "+34 600 123 456") @Size(max = 50) String customerPhone
) {}
