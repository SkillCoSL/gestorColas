package com.queuetable.table.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear una nueva mesa")
public record CreateTableRequest(
        @Schema(description = "Etiqueta de la mesa", example = "Mesa 1") @NotBlank @Size(max = 100) String label,
        @Schema(description = "Capacidad de comensales", example = "4") @Min(1) int capacity,
        @Schema(description = "Zona del restaurante", example = "Terraza") @Size(max = 100) String zone
) {}
