package com.queuetable.restaurant.adapter.in;

import com.queuetable.restaurant.domain.QrCodeService;
import com.queuetable.restaurant.domain.RestaurantService;
import com.queuetable.restaurant.dto.RestaurantResponse;
import com.queuetable.restaurant.dto.RestaurantUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/restaurants")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Restaurante", description = "CRUD de restaurante y generacion de codigo QR")
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final QrCodeService qrCodeService;

    public RestaurantController(RestaurantService restaurantService, QrCodeService qrCodeService) {
        this.restaurantService = restaurantService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener restaurante por ID")
    @ApiResponse(responseCode = "200", description = "Restaurante encontrado")
    @ApiResponse(responseCode = "404", description = "Restaurante no encontrado")
    public ResponseEntity<RestaurantResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(RestaurantResponse.from(restaurantService.getById(id)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Actualizar datos del restaurante")
    @ApiResponse(responseCode = "200", description = "Restaurante actualizado")
    @ApiResponse(responseCode = "404", description = "Restaurante no encontrado")
    public ResponseEntity<RestaurantResponse> update(@PathVariable UUID id,
                                                     @Valid @RequestBody RestaurantUpdateRequest request) {
        return ResponseEntity.ok(RestaurantResponse.from(restaurantService.update(id, request)));
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Generar codigo QR del restaurante", description = "Devuelve imagen PNG con el QR que apunta a la pagina publica del restaurante")
    @ApiResponse(responseCode = "200", description = "Imagen QR generada")
    @ApiResponse(responseCode = "404", description = "Restaurante no encontrado")
    public ResponseEntity<byte[]> getQrCode(@PathVariable UUID id) {
        var restaurant = restaurantService.getById(id);
        byte[] qrImage = qrCodeService.generateQrCode(restaurant.getSlug());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrImage);
    }
}
