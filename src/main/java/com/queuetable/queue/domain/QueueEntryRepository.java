package com.queuetable.queue.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {

    Optional<QueueEntry> findByIdAndAccessToken(UUID id, UUID accessToken);

    List<QueueEntry> findByRestaurantIdAndStatusOrderByPositionAsc(UUID restaurantId, QueueEntryStatus status);

    List<QueueEntry> findByRestaurantIdOrderByPositionAsc(UUID restaurantId);

    @Query("SELECT COUNT(e) FROM QueueEntry e WHERE e.restaurantId = :restaurantId AND e.status = 'WAITING'")
    int countWaiting(UUID restaurantId);

    @Query("SELECT COALESCE(MAX(e.position), 0) FROM QueueEntry e WHERE e.restaurantId = :restaurantId")
    int findMaxPosition(UUID restaurantId);

    List<QueueEntry> findByRestaurantIdAndStatusInOrderByPositionAsc(UUID restaurantId, List<QueueEntryStatus> statuses);

    List<QueueEntry> findByStatusAndNotifiedAtBefore(QueueEntryStatus status, Instant cutoff);
}
