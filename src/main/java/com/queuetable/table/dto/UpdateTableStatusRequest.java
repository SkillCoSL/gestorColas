package com.queuetable.table.dto;

import com.queuetable.table.domain.TableStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Nuevo estado de la mesa")
public record UpdateTableStatusRequest(
        @Schema(description = "Estado destino: FREE, OCCUPIED, CLEANING") @NotNull TableStatus status
) {}
