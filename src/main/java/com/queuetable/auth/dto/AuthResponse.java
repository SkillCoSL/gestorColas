package com.queuetable.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Tokens JWT y datos basicos del restaurante")
public record AuthResponse(
        @Schema(description = "JWT access token (expira en 15 min)") String accessToken,
        @Schema(description = "Refresh token (expira en 7 dias)") String refreshToken,
        UUID restaurantId,
        @Schema(example = "La Trattoria") String restaurantName,
        @Schema(example = "la-trattoria") String restaurantSlug
) {}
