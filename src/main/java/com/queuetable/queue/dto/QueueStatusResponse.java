package com.queuetable.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado actual de la cola del restaurante")
public record QueueStatusResponse(
        @Schema(description = "Personas esperando", example = "5") int waitingCount,
        @Schema(description = "Tiempo estimado en minutos para el partySize indicado; null si no se aporta partySize o si no hay mesa compatible", example = "15") Integer estimatedWaitMinutes,
        @Schema(description = "Si la cola esta abierta") boolean queueOpen
) {}
