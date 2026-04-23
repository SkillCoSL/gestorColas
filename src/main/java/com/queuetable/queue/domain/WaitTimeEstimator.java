package com.queuetable.queue.domain;

import com.queuetable.config.domain.RestaurantConfig;
import com.queuetable.table.domain.RestaurantTable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

@Component
public class WaitTimeEstimator {

    public Integer estimate(int partySize,
                            int competitorsAhead,
                            List<RestaurantTable> tables,
                            Set<UUID> excludedTableIds,
                            RestaurantConfig config,
                            Instant now) {
        if (partySize < 1) {
            return null;
        }

        List<RestaurantTable> pool = tables.stream()
                .filter(t -> t.getCapacity() >= partySize)
                .filter(t -> !excludedTableIds.contains(t.getId()))
                .toList();

        if (pool.isEmpty()) {
            return null;
        }

        PriorityQueue<Integer> heap = new PriorityQueue<>();
        for (RestaurantTable t : pool) {
            heap.offer(remainingMinutes(t, config, now));
        }

        int turnDuration = config.getAvgTableDurationMinutes() + config.getCleaningDurationMinutes();
        for (int i = 0; i < competitorsAhead; i++) {
            int next = heap.poll();
            heap.offer(next + turnDuration);
        }

        return heap.peek();
    }

    int remainingMinutes(RestaurantTable table, RestaurantConfig config, Instant now) {
        return switch (table.getStatus()) {
            case FREE -> 0;
            case CLEANING -> {
                Instant startedAt = table.getCleaningStartedAt();
                if (startedAt == null) {
                    yield config.getCleaningDurationMinutes();
                }
                long elapsed = ChronoUnit.MINUTES.between(startedAt, now);
                yield (int) Math.max(0L, config.getCleaningDurationMinutes() - elapsed);
            }
            case OCCUPIED -> {
                Instant startedAt = table.getOccupiedAt();
                if (startedAt == null) {
                    yield config.getAvgTableDurationMinutes() / 2 + config.getCleaningDurationMinutes();
                }
                long elapsed = ChronoUnit.MINUTES.between(startedAt, now);
                long remaining = config.getAvgTableDurationMinutes() - elapsed;
                yield (int) Math.max(config.getWaitBufferMinutes(), remaining)
                        + config.getCleaningDurationMinutes();
            }
        };
    }
}
