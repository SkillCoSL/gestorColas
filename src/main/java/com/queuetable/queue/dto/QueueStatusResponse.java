package com.queuetable.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado actual de la cola del restaurante")
public record QueueStatusResponse(
        @Schema(description = "Personas esperando", example = "5") int waitingCount,
        @Schema(description = "Tiempo estimado para el siguiente", example = "15") int estimatedWaitMinutes,
        @Schema(description = "Si la cola esta abierta") boolean queueOpen
) {}
