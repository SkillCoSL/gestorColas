package com.queuetable.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciales de login")
public record LoginRequest(
        @Schema(example = "admin@latrattoria.com") @NotBlank @Email String email,
        @Schema(example = "SecurePass123") @NotBlank String password
) {}
