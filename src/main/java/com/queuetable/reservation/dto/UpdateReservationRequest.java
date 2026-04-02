package com.queuetable.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateReservationRequest(
        @Size(max = 200) String customerName,
        @Size(max = 50) String customerPhone,
        @Min(1) Integer partySize,
        Instant reservedAt,
        String notes
) {}
