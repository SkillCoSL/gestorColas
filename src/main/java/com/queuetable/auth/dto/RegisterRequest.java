package com.queuetable.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para registrar un nuevo restaurante y su staff admin")
public record RegisterRequest(
        @Schema(description = "Nombre del restaurante", example = "La Trattoria") @NotBlank @Size(max = 255) String restaurantName,
        @Schema(description = "Slug unico (URL-friendly)", example = "la-trattoria") @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,98}[a-z0-9]$",
                message = "Slug must be lowercase alphanumeric with hyphens, 3-100 chars")
        String restaurantSlug,
        @Schema(description = "Direccion del restaurante", example = "Calle Mayor 10, Madrid") @NotBlank @Size(max = 500) String restaurantAddress,
        @Schema(description = "Email del admin", example = "admin@latrattoria.com") @NotBlank @Email String email,
        @Schema(description = "Password (min 8 caracteres)", example = "SecurePass123") @NotBlank @Size(min = 8, max = 100) String password,
        @Schema(description = "Nombre del staff admin", example = "Carlos Garcia") @NotBlank @Size(max = 255) String staffName
) {}
