package com.queuetable.config.dto;

import com.queuetable.config.domain.RestaurantConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Configuracion actual del restaurante")
public record RestaurantConfigResponse(
        UUID id,
        UUID restaurantId,
        int confirmationTimeoutMinutes,
        int noshowGraceMinutes,
        int avgTableDurationMinutes,
        int reservationProtectionWindowMinutes,
        Integer maxQueueSize
) {
    public static RestaurantConfigResponse from(RestaurantConfig c) {
        return new RestaurantConfigResponse(
                c.getId(), c.getRestaurantId(),
                c.getConfirmationTimeoutMinutes(), c.getNoshowGraceMinutes(),
                c.getAvgTableDurationMinutes(), c.getReservationProtectionWindowMinutes(),
                c.getMaxQueueSize()
        );
    }
}
