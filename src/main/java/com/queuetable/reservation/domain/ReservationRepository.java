package com.queuetable.reservation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByRestaurantIdAndStatusOrderByReservedAtAsc(UUID restaurantId, ReservationStatus status);

    List<Reservation> findByRestaurantIdOrderByReservedAtAsc(UUID restaurantId);

    List<Reservation> findByRestaurantIdAndReservedAtBetweenOrderByReservedAtAsc(
            UUID restaurantId, Instant from, Instant to);

    List<Reservation> findByRestaurantIdAndStatusAndReservedAtBetweenOrderByReservedAtAsc(
            UUID restaurantId, ReservationStatus status, Instant from, Instant to);

    List<Reservation> findByTableIdAndStatusIn(UUID tableId, List<ReservationStatus> statuses);

    List<Reservation> findByStatusAndReservedAtBefore(ReservationStatus status, Instant cutoff);
}
