package com.queuetable.queue.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SeatQueueEntryRequest(
        @NotNull UUID tableId
) {}
