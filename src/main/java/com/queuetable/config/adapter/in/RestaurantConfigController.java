package com.queuetable.config.adapter.in;

import com.queuetable.config.domain.RestaurantConfigService;
import com.queuetable.config.dto.RestaurantConfigResponse;
import com.queuetable.config.dto.RestaurantConfigUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/restaurants/{restaurantId}/config")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Configuracion", description = "Timeouts, duraciones y limites del restaurante")
public class RestaurantConfigController {

    private final RestaurantConfigService configService;

    public RestaurantConfigController(RestaurantConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    @Operation(summary = "Obtener configuracion del restaurante")
    @ApiResponse(responseCode = "200", description = "Configuracion encontrada")
    @ApiResponse(responseCode = "404", description = "Restaurante no encontrado")
    public ResponseEntity<RestaurantConfigResponse> get(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(RestaurantConfigResponse.from(configService.getByRestaurantId(restaurantId)));
    }

    @PatchMapping
    @Operation(summary = "Actualizar configuracion del restaurante")
    @ApiResponse(responseCode = "200", description = "Configuracion actualizada")
    @ApiResponse(responseCode = "404", description = "Restaurante no encontrado")
    public ResponseEntity<RestaurantConfigResponse> update(@PathVariable UUID restaurantId,
                                                           @Valid @RequestBody RestaurantConfigUpdateRequest request) {
        return ResponseEntity.ok(RestaurantConfigResponse.from(configService.update(restaurantId, request)));
    }
}
