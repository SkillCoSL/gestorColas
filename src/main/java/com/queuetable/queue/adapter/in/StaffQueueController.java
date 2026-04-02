package com.queuetable.queue.adapter.in;

import com.queuetable.queue.domain.QueueEntryStatus;
import com.queuetable.queue.domain.QueueService;
import com.queuetable.queue.dto.JoinQueueRequest;
import com.queuetable.queue.dto.QueueEntryResponse;
import com.queuetable.queue.dto.SeatQueueEntryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Cola staff", description = "Gestion de la cola por el personal del restaurante")
public class StaffQueueController {

    private final QueueService queueService;

    public StaffQueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/restaurants/{restaurantId}/queue")
    @Operation(summary = "Listar cola del restaurante", description = "Filtrable por estado: WAITING, NOTIFIED, CONFIRMED, SEATED, CANCELLED, NO_SHOW, SKIPPED")
    @ApiResponse(responseCode = "200", description = "Lista de entradas en la cola")
    public ResponseEntity<List<QueueEntryResponse>> listQueue(
            @PathVariable UUID restaurantId,
            @Parameter(description = "Filtrar por estado") @RequestParam(required = false) QueueEntryStatus status) {
        return ResponseEntity.ok(queueService.listQueue(restaurantId, status));
    }

    @PostMapping("/restaurants/{restaurantId}/queue")
    @Operation(summary = "Agregar walk-in a la cola", description = "Staff registra un cliente que llego sin escanear QR")
    @ApiResponse(responseCode = "201", description = "Walk-in agregado a la cola")
    public ResponseEntity<QueueEntryResponse> addWalkIn(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody JoinQueueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(queueService.addWalkIn(restaurantId, request));
    }

    @PostMapping("/restaurants/{restaurantId}/queue/{entryId}/seat")
    @Operation(summary = "Sentar entrada de la cola en una mesa")
    @ApiResponse(responseCode = "200", description = "Cliente sentado")
    @ApiResponse(responseCode = "400", description = "Mesa no disponible o entrada en estado invalido")
    @ApiResponse(responseCode = "404", description = "Entrada o mesa no encontrada")
    public ResponseEntity<QueueEntryResponse> seatEntry(
            @PathVariable UUID restaurantId,
            @PathVariable UUID entryId,
            @Valid @RequestBody SeatQueueEntryRequest request) {
        return ResponseEntity.ok(queueService.seatEntry(restaurantId, entryId, request.tableId()));
    }

    @PostMapping("/restaurants/{restaurantId}/queue/{entryId}/notify")
    @Operation(summary = "Notificar al cliente que su mesa esta lista")
    @ApiResponse(responseCode = "200", description = "Cliente notificado")
    @ApiResponse(responseCode = "400", description = "Entrada no esta en estado WAITING")
    @ApiResponse(responseCode = "404", description = "Entrada no encontrada")
    public ResponseEntity<QueueEntryResponse> notifyEntry(
            @PathVariable UUID restaurantId,
            @PathVariable UUID entryId) {
        return ResponseEntity.ok(queueService.notifyEntry(restaurantId, entryId));
    }

    @PostMapping("/restaurants/{restaurantId}/queue/{entryId}/skip")
    @Operation(summary = "Saltar una entrada en la cola", description = "Marca la entrada como SKIPPED y avanza la cola")
    @ApiResponse(responseCode = "200", description = "Entrada saltada")
    @ApiResponse(responseCode = "404", description = "Entrada no encontrada")
    public ResponseEntity<QueueEntryResponse> skipEntry(
            @PathVariable UUID restaurantId,
            @PathVariable UUID entryId) {
        return ResponseEntity.ok(queueService.skipEntry(restaurantId, entryId));
    }

    @PostMapping("/restaurants/{restaurantId}/queue/{entryId}/cancel")
    @Operation(summary = "Cancelar entrada de la cola")
    @ApiResponse(responseCode = "204", description = "Entrada cancelada")
    @ApiResponse(responseCode = "404", description = "Entrada no encontrada")
    public ResponseEntity<Void> cancelEntry(@PathVariable UUID restaurantId,
                                            @PathVariable UUID entryId) {
        queueService.cancelEntryByStaff(restaurantId, entryId);
        return ResponseEntity.noContent().build();
    }
}
