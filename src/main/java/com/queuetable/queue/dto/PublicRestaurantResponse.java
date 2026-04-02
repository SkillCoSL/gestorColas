package com.queuetable.queue.dto;

import com.queuetable.restaurant.domain.Restaurant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Informacion publica del restaurante (sin datos sensibles)")
public record PublicRestaurantResponse(
        String name,
        String slug,
        String description,
        Map<String, Object> openingHours,
        boolean active
) {
    public static PublicRestaurantResponse from(Restaurant r) {
        return new PublicRestaurantResponse(
                r.getName(), r.getSlug(), r.getDescription(),
                r.getOpeningHours(), r.isActive()
        );
    }
}
