package com.queuetable.reservation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SeatReservationRequest(
        @NotNull UUID tableId
) {}
