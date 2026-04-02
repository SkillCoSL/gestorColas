package com.queuetable.queue.domain;

import java.util.Map;
import java.util.Set;

public enum QueueEntryStatus {
    WAITING,
    NOTIFIED,
    SEATED,
    CANCELLED,
    EXPIRED;

    private static final Map<QueueEntryStatus, Set<QueueEntryStatus>> VALID_TRANSITIONS = Map.of(
            WAITING, Set.of(NOTIFIED, SEATED, CANCELLED),
            NOTIFIED, Set.of(SEATED, EXPIRED, CANCELLED)
    );

    public boolean canTransitionTo(QueueEntryStatus target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isActive() {
        return this == WAITING || this == NOTIFIED;
    }
}
