package com.queuetable.reservation.adapter.in;

import com.queuetable.reservation.domain.ReservationService;
import com.queuetable.reservation.domain.ReservationStatus;
import com.queuetable.reservation.dto.CreateReservationRequest;
import com.queuetable.reservation.dto.ReservationResponse;
import com.queuetable.reservation.dto.SeatReservationRequest;
import com.queuetable.reservation.dto.UpdateReservationRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/restaurants/{restaurantId}/reservations")
    public ResponseEntity<List<ReservationResponse>> list(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reservationService.list(restaurantId, status, date));
    }

    @PostMapping("/restaurants/{restaurantId}/reservations")
    public ResponseEntity<ReservationResponse> create(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.create(restaurantId, request));
    }

    @PatchMapping("/reservations/{id}")
    public ResponseEntity<ReservationResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReservationRequest request) {
        return ResponseEntity.ok(reservationService.update(id, request));
    }

    @PostMapping("/reservations/{id}/arrive")
    public ResponseEntity<ReservationResponse> arrive(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.arrive(id));
    }

    @PostMapping("/reservations/{id}/seat")
    public ResponseEntity<ReservationResponse> seat(
            @PathVariable UUID id,
            @Valid @RequestBody SeatReservationRequest request) {
        return ResponseEntity.ok(reservationService.seat(id, request));
    }

    @PostMapping("/reservations/{id}/no-show")
    public ResponseEntity<ReservationResponse> noShow(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.markNoShow(id));
    }

    @PostMapping("/reservations/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.cancel(id));
    }
}
