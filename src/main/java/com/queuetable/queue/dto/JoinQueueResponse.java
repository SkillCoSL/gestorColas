package com.queuetable.queue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Respuesta al unirse a la cola")
public record JoinQueueResponse(
        @Schema(description = "ID de la entrada en cola") UUID entryId,
        @Schema(description = "Token para consultar/cancelar la entrada") UUID accessToken,
        @Schema(description = "Posicion en la cola", example = "3") int position,
        @Schema(description = "Tiempo estimado de espera en minutos", example = "20") int estimatedWaitMinutes
) {}
