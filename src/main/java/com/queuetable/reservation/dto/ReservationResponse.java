package com.queuetable.reservation.dto;

import com.queuetable.reservation.domain.Reservation;
import com.queuetable.reservation.domain.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Datos de una reserva")
public record ReservationResponse(
        UUID id,
        UUID restaurantId,
        UUID tableId,
        String customerName,
        String customerPhone,
        int partySize,
        Instant reservedAt,
        ReservationStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(), r.getRestaurantId(), r.getTableId(),
                r.getCustomerName(), r.getCustomerPhone(),
                r.getPartySize(), r.getReservedAt(),
                r.getStatus(), r.getNotes(),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
