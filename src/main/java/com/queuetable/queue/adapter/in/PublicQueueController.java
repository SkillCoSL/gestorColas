package com.queuetable.queue.adapter.in;

import com.queuetable.queue.domain.QueueService;
import com.queuetable.queue.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/public")
@Tag(name = "Cola publica", description = "Endpoints publicos para clientes — sin autenticacion")
public class PublicQueueController {

    private final QueueService queueService;

    public PublicQueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/restaurants/{slug}")
    @Operation(summary = "Informacion publica del restaurante por slug")
    @ApiResponse(responseCode = "200", description = "Info del restaurante")
    @ApiResponse(responseCode = "404", description = "Restaurante no encontrado")
    public ResponseEntity<PublicRestaurantResponse> getRestaurantInfo(@PathVariable String slug) {
        return ResponseEntity.ok(queueService.getPublicRestaurantInfo(slug));
    }

    @GetMapping("/restaurants/{slug}/queue/status")
    @Operation(summary = "Estado actual de la cola", description = "Cuantas personas esperan y tiempo estimado")
    @ApiResponse(responseCode = "200", description = "Estado de la cola")
    public ResponseEntity<QueueStatusResponse> getQueueStatus(@PathVariable String slug) {
        return ResponseEntity.ok(queueService.getQueueStatus(slug));
    }

    @PostMapping("/restaurants/{slug}/queue")
    @Operation(summary = "Unirse a la cola", description = "El cliente se apunta a la cola y recibe un accessToken para seguimiento")
    @ApiResponse(responseCode = "201", description = "Entrada creada en la cola")
    @ApiResponse(responseCode = "409", description = "Cola llena o cerrada")
    public ResponseEntity<JoinQueueResponse> joinQueue(@PathVariable String slug,
                                                       @Valid @RequestBody JoinQueueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(queueService.joinQueue(slug, request));
    }

    @GetMapping("/queue/{entryId}")
    @Operation(summary = "Consultar estado de mi entrada en la cola")
    @ApiResponse(responseCode = "200", description = "Estado de la entrada")
    @ApiResponse(responseCode = "403", description = "Access token invalido")
    @ApiResponse(responseCode = "404", description = "Entrada no encontrada")
    public ResponseEntity<PublicQueueEntryResponse> getEntryStatus(
            @PathVariable UUID entryId,
            @Parameter(description = "Token de acceso recibido al unirse") @RequestParam UUID accessToken) {
        return ResponseEntity.ok(queueService.getEntryStatus(entryId, accessToken));
    }

    @PostMapping("/queue/{entryId}/confirm")
    @Operation(summary = "Confirmar asistencia tras notificacion")
    @ApiResponse(responseCode = "200", description = "Asistencia confirmada")
    @ApiResponse(responseCode = "400", description = "La entrada no esta en estado NOTIFIED")
    @ApiResponse(responseCode = "404", description = "Entrada no encontrada")
    public ResponseEntity<PublicQueueEntryResponse> confirmEntry(
            @PathVariable UUID entryId,
            @Parameter(description = "Token de acceso recibido al unirse") @RequestParam UUID accessToken) {
        return ResponseEntity.ok(queueService.confirmEntry(entryId, accessToken));
    }

    @DeleteMapping("/queue/{entryId}")
    @Operation(summary = "Cancelar mi entrada en la cola")
    @ApiResponse(responseCode = "204", description = "Entrada cancelada")
    @ApiResponse(responseCode = "403", description = "Access token invalido")
    @ApiResponse(responseCode = "404", description = "Entrada no encontrada")
    public ResponseEntity<Void> cancelEntry(@PathVariable UUID entryId,
                                            @Parameter(description = "Token de acceso recibido al unirse") @RequestParam UUID accessToken) {
        queueService.cancelEntryByCustomer(entryId, accessToken);
        return ResponseEntity.noContent().build();
    }
}
