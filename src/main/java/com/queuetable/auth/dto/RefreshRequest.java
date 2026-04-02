package com.queuetable.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh token para renovar el access token")
public record RefreshRequest(
        @Schema(description = "Refresh token obtenido en login/register") @NotBlank String refreshToken
) {}
