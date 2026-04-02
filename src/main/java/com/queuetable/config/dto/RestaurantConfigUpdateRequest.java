package com.queuetable.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "Configuracion del restaurante (todos los campos opcionales)")
public record RestaurantConfigUpdateRequest(
        @Schema(description = "Tiempo para confirmar tras notificacion", example = "5") @Min(1) Integer confirmationTimeoutMinutes,
        @Schema(description = "Gracia antes de marcar no-show", example = "15") @Min(1) Integer noshowGraceMinutes,
        @Schema(description = "Duracion promedio de una mesa", example = "60") @Min(1) Integer avgTableDurationMinutes,
        @Schema(description = "Ventana de proteccion de reserva", example = "30") @Min(1) Integer reservationProtectionWindowMinutes,
        @Schema(description = "Tamanio maximo de la cola (null = sin limite)", example = "50") @Min(1) Integer maxQueueSize
) {}
