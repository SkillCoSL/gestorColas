package com.queuetable.table.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "Campos a actualizar de la mesa (todos opcionales)")
public record UpdateTableRequest(
        @Schema(example = "Mesa VIP 1") @Size(max = 100) String label,
        @Schema(example = "6") @Min(1) Integer capacity,
        @Schema(example = "Interior") @Size(max = 100) String zone
) {}
