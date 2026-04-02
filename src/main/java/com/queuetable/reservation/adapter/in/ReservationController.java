package com.queuetable.reservation.adapter.in;

import com.queuetable.reservation.domain.ReservationService;
import com.queuetable.reservation.domain.ReservationStatus;
import com.queuetable.reservation.dto.CreateReservationRequest;
import com.queuetable.reservation.dto.ReservationResponse;
import com.queuetable.reservation.dto.SeatReservationRequest;
import com.queuetable.reservation.dto.UpdateReservationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Reservas", description = "CRUD de reservas y transiciones de estado")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/restaurants/{restaurantId}/reservations")
    @Operation(summary = "Listar reservas del restaurante", description = "Filtrable por estado y fecha")
    @ApiResponse(responseCode = "200", description = "Lista de reservas")
    public ResponseEntity<List<ReservationResponse>> list(
            @PathVariable UUID restaurantId,
            @Parameter(description = "Filtrar por estado: PENDING, ARRIVED, SEATED, NO_SHOW, CANCELLED") @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "Filtrar por fecha (ISO: yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reservationService.list(restaurantId, status, date));
    }

    @PostMapping("/restaurants/{restaurantId}/reservations")
    @Operation(summary = "Crear nueva reserva")
    @ApiResponse(responseCode = "201", description = "Reserva creada")
    public ResponseEntity<ReservationResponse> create(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.create(restaurantId, request));
    }

    @PatchMapping("/reservations/{id}")
    @Operation(summary = "Actualizar datos de una reserva")
    @ApiResponse(responseCode = "200", description = "Reserva actualizada")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public ResponseEntity<ReservationResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReservationRequest request) {
        return ResponseEntity.ok(reservationService.update(id, request));
    }

    @PostMapping("/reservations/{id}/arrive")
    @Operation(summary = "Marcar llegada del cliente con reserva")
    @ApiResponse(responseCode = "200", description = "Llegada registrada")
    @ApiResponse(responseCode = "400", description = "Reserva no esta en estado PENDING")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public ResponseEntity<ReservationResponse> arrive(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.arrive(id));
    }

    @PostMapping("/reservations/{id}/seat")
    @Operation(summary = "Sentar reserva en una mesa")
    @ApiResponse(responseCode = "200", description = "Reserva sentada")
    @ApiResponse(responseCode = "400", description = "Mesa no disponible o estado invalido")
    @ApiResponse(responseCode = "404", description = "Reserva o mesa no encontrada")
    public ResponseEntity<ReservationResponse> seat(
            @PathVariable UUID id,
            @Valid @RequestBody SeatReservationRequest request) {
        return ResponseEntity.ok(reservationService.seat(id, request));
    }

    @PostMapping("/reservations/{id}/no-show")
    @Operation(summary = "Marcar reserva como no-show")
    @ApiResponse(responseCode = "200", description = "Marcada como no-show")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public ResponseEntity<ReservationResponse> noShow(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.markNoShow(id));
    }

    @PostMapping("/reservations/{id}/cancel")
    @Operation(summary = "Cancelar una reserva")
    @ApiResponse(responseCode = "200", description = "Reserva cancelada")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public ResponseEntity<ReservationResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.cancel(id));
    }
}
