package com.queuetable.reservation.domain;

import java.util.Map;
import java.util.Set;

public enum ReservationStatus {
    BOOKED,
    ARRIVED,
    SEATED,
    CANCELLED,
    NO_SHOW,
    COMPLETED;

    private static final Map<ReservationStatus, Set<ReservationStatus>> VALID_TRANSITIONS = Map.of(
            BOOKED, Set.of(ARRIVED, CANCELLED, NO_SHOW),
            ARRIVED, Set.of(SEATED),
            SEATED, Set.of(COMPLETED)
    );

    public boolean canTransitionTo(ReservationStatus target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isActive() {
        return this == BOOKED || this == ARRIVED || this == SEATED;
    }
}
