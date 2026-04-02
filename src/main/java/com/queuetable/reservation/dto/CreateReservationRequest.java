package com.queuetable.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateReservationRequest(
        @NotBlank @Size(max = 200) String customerName,
        @Size(max = 50) String customerPhone,
        @Min(1) int partySize,
        @NotNull Instant reservedAt,
        String notes
) {}
