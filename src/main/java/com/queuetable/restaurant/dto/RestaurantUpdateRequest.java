package com.queuetable.restaurant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(description = "Campos a actualizar del restaurante (todos opcionales)")
public record RestaurantUpdateRequest(
        @Schema(example = "La Trattoria Renovada") @Size(max = 255) String name,
        @Schema(example = "Avenida Central 5, Madrid") @Size(max = 500) String address,
        @Schema(example = "+34 912 345 678") @Size(max = 50) String phone,
        @Schema(example = "Cocina italiana tradicional") String description,
        @Schema(description = "Horarios de apertura por dia") Map<String, Object> openingHours
) {}
