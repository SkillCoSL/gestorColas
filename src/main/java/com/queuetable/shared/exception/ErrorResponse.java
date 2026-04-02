package com.queuetable.shared.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Respuesta de error estandar")
public record ErrorResponse(
        @Schema(description = "Mensaje de error", example = "Resource not found") String error,
        @Schema(description = "Codigo de error", example = "NOT_FOUND") String code,
        @Schema(description = "HTTP status code", example = "404") int status,
        @Schema(description = "Timestamp del error") Instant timestamp
) {
    public static ErrorResponse of(String error, String code, int status) {
        return new ErrorResponse(error, code, status, Instant.now());
    }
}
