package com.queuetable.reservation.domain;

import com.queuetable.config.domain.RestaurantConfig;
import com.queuetable.config.domain.RestaurantConfigRepository;
import com.queuetable.reservation.dto.CreateReservationRequest;
import com.queuetable.reservation.dto.ReservationResponse;
import com.queuetable.reservation.dto.SeatReservationRequest;
import com.queuetable.reservation.dto.UpdateReservationRequest;
import com.queuetable.shared.event.QueueRecalculationEvent;
import com.queuetable.shared.exception.BadRequestException;
import com.queuetable.shared.exception.ForbiddenException;
import com.queuetable.shared.exception.ResourceNotFoundException;
import com.queuetable.shared.security.SecurityContextUtil;
import com.queuetable.shared.websocket.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import com.queuetable.table.domain.RestaurantTable;
import com.queuetable.table.domain.TableRepository;
import com.queuetable.table.dto.TableResponse;
import com.queuetable.table.domain.TableStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.time.temporal.ChronoUnit;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final RestaurantConfigRepository configRepository;
    private final EventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ReservationService(ReservationRepository reservationRepository,
                              TableRepository tableRepository,
                              RestaurantConfigRepository configRepository,
                              EventPublisher eventPublisher,
                              ApplicationEventPublisher applicationEventPublisher) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.configRepository = configRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(UUID restaurantId, ReservationStatus status, LocalDate date) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);

        List<Reservation> reservations;

        if (date != null && status != null) {
            Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            reservations = reservationRepository
                    .findByRestaurantIdAndStatusAndReservedAtBetweenOrderByReservedAtAsc(
                            restaurantId, status, from, to);
        } else if (date != null) {
            Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            reservations = reservationRepository
                    .findByRestaurantIdAndReservedAtBetweenOrderByReservedAtAsc(
                            restaurantId, from, to);
        } else if (status != null) {
            reservations = reservationRepository
                    .findByRestaurantIdAndStatusOrderByReservedAtAsc(restaurantId, status);
        } else {
            reservations = reservationRepository
                    .findByRestaurantIdOrderByReservedAtAsc(restaurantId);
        }

        return reservations.stream().map(ReservationResponse::from).toList();
    }

    @Transactional
    public ReservationResponse create(UUID restaurantId, CreateReservationRequest request) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);
        RestaurantTable table = validateAssignedTable(
                restaurantId,
                request.tableId(),
                request.partySize(),
                request.reservedAt(),
                null
        );

        Reservation reservation = new Reservation(
                restaurantId,
                request.customerName(),
                request.partySize(),
                request.reservedAt()
        );
        if (request.customerPhone() != null) {
            reservation.setCustomerPhone(request.customerPhone());
        }
        if (request.notes() != null) {
            reservation.setNotes(request.notes());
        }
        reservation.setTableId(table.getId());

        ReservationResponse response = ReservationResponse.from(reservationRepository.save(reservation));
        eventPublisher.publishReservationUpdated(restaurantId, response);
        return response;
    }

    @Transactional
    public ReservationResponse update(UUID reservationId, UpdateReservationRequest request) {
        Reservation reservation = findAndValidateOwnership(reservationId);

        if (reservation.getStatus() != ReservationStatus.BOOKED) {
            throw new BadRequestException("Can only edit reservations in BOOKED status");
        }

        int nextPartySize = request.partySize() != null ? request.partySize() : reservation.getPartySize();
        Instant nextReservedAt = request.reservedAt() != null ? request.reservedAt() : reservation.getReservedAt();
        UUID nextTableId = request.tableId() != null ? request.tableId() : reservation.getTableId();

        RestaurantTable table = validateAssignedTable(
                reservation.getRestaurantId(),
                nextTableId,
                nextPartySize,
                nextReservedAt,
                reservation.getId()
        );

        if (request.customerName() != null) reservation.setCustomerName(request.customerName());
        if (request.customerPhone() != null) reservation.setCustomerPhone(request.customerPhone());
        reservation.setPartySize(nextPartySize);
        reservation.setReservedAt(nextReservedAt);
        reservation.setTableId(table.getId());
        if (request.notes() != null) reservation.setNotes(request.notes());

        return saveAndPublish(reservation);
    }

    @Transactional
    public ReservationResponse arrive(UUID reservationId) {
        Reservation reservation = findAndValidateOwnership(reservationId);
        transition(reservation, ReservationStatus.ARRIVED);
        return saveAndPublish(reservation);
    }

    @Transactional
    public ReservationResponse seat(UUID reservationId, SeatReservationRequest request) {
        Reservation reservation = findAndValidateOwnership(reservationId);
        transition(reservation, ReservationStatus.SEATED);

        RestaurantTable table = tableRepository.findById(request.tableId())
                .orElseThrow(() -> new ResourceNotFoundException("Table", request.tableId()));

        if (!table.getRestaurantId().equals(reservation.getRestaurantId())) {
            throw new ForbiddenException("Table does not belong to this restaurant");
        }
        if (table.getStatus() != TableStatus.FREE) {
            throw new BadRequestException("Table is not free, current status: " + table.getStatus());
        }

        reservation.setTableId(table.getId());
        table.setStatus(TableStatus.OCCUPIED);
        table.setOccupiedAt(Instant.now());
        table.setCleaningStartedAt(null);

        tableRepository.save(table);
        eventPublisher.publishTableUpdated(reservation.getRestaurantId(), TableResponse.from(table));
        applicationEventPublisher.publishEvent(new QueueRecalculationEvent(reservation.getRestaurantId()));
        return saveAndPublish(reservation);
    }

    @Transactional
    public ReservationResponse markNoShow(UUID reservationId) {
        Reservation reservation = findAndValidateOwnership(reservationId);
        transition(reservation, ReservationStatus.NO_SHOW);
        return saveAndPublish(reservation);
    }

    @Transactional
    public ReservationResponse complete(UUID reservationId) {
        Reservation reservation = findAndValidateOwnership(reservationId);
        transition(reservation, ReservationStatus.COMPLETED);

        // Release table → CLEANING
        if (reservation.getTableId() != null) {
            RestaurantTable table = tableRepository.findById(reservation.getTableId())
                    .orElse(null);
            if (table != null && table.getStatus() == TableStatus.OCCUPIED) {
                table.setStatus(TableStatus.CLEANING);
                table.setCleaningStartedAt(Instant.now());
                table.setOccupiedAt(null);
                tableRepository.save(table);
                eventPublisher.publishTableUpdated(reservation.getRestaurantId(), TableResponse.from(table));
            }
        }

        ReservationResponse response = saveAndPublish(reservation);
        applicationEventPublisher.publishEvent(new QueueRecalculationEvent(reservation.getRestaurantId()));
        return response;
    }

    @Transactional
    public ReservationResponse cancel(UUID reservationId) {
        Reservation reservation = findAndValidateOwnership(reservationId);
        transition(reservation, ReservationStatus.CANCELLED);
        ReservationResponse response = saveAndPublish(reservation);
        applicationEventPublisher.publishEvent(new QueueRecalculationEvent(reservation.getRestaurantId()));
        return response;
    }

    private ReservationResponse saveAndPublish(Reservation reservation) {
        ReservationResponse response = ReservationResponse.from(reservationRepository.save(reservation));
        eventPublisher.publishReservationUpdated(reservation.getRestaurantId(), response);
        return response;
    }

    private void transition(Reservation reservation, ReservationStatus target) {
        if (!reservation.getStatus().canTransitionTo(target)) {
            throw new BadRequestException(
                    "Invalid transition: " + reservation.getStatus() + " → " + target);
        }
        ReservationStatus from = reservation.getStatus();
        reservation.setStatus(target);

        log.info("action=reservation_transition reservationId={} restaurantId={} from={} to={} customer={}",
                reservation.getId(), reservation.getRestaurantId(), from, target, reservation.getCustomerName());
    }

    private Reservation findAndValidateOwnership(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId));
        SecurityContextUtil.validateRestaurantOwnership(reservation.getRestaurantId());
        return reservation;
    }

    private RestaurantTable validateAssignedTable(
            UUID restaurantId,
            UUID tableId,
            int partySize,
            Instant reservedAt,
            UUID currentReservationId
    ) {
        if (tableId == null) {
            throw new BadRequestException("Reservation requires an assigned table");
        }

        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));

        if (!table.getRestaurantId().equals(restaurantId)) {
            throw new ForbiddenException("Table does not belong to this restaurant");
        }
        if (table.getStatus() != TableStatus.FREE) {
            throw new BadRequestException("Assigned table must be free, current status: " + table.getStatus());
        }
        if (table.getCapacity() < partySize) {
            throw new BadRequestException("Assigned table capacity is smaller than the reservation party size");
        }

        ensureTableHasNoOverlappingReservation(restaurantId, tableId, reservedAt, currentReservationId);
        return table;
    }

    private void ensureTableHasNoOverlappingReservation(
            UUID restaurantId,
            UUID tableId,
            Instant reservedAt,
            UUID currentReservationId
    ) {
        RestaurantConfig config = configRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("RestaurantConfig", restaurantId));

        long avgDurationMinutes = config.getAvgTableDurationMinutes();
        Instant requestedEnd = reservedAt.plus(avgDurationMinutes, ChronoUnit.MINUTES);

        List<Reservation> activeReservations = reservationRepository.findByTableIdAndStatusIn(
                tableId,
                List.of(ReservationStatus.BOOKED, ReservationStatus.ARRIVED, ReservationStatus.SEATED)
        );

        boolean conflictExists = activeReservations.stream()
                .filter(existing -> currentReservationId == null || !existing.getId().equals(currentReservationId))
                .anyMatch(existing -> {
                    Instant existingStart = existing.getReservedAt();
                    Instant existingEnd = existingStart.plus(avgDurationMinutes, ChronoUnit.MINUTES);
                    return reservedAt.isBefore(existingEnd) && existingStart.isBefore(requestedEnd);
                });

        if (conflictExists) {
            throw new BadRequestException("Assigned table already has another active reservation in that time slot");
        }
    }
}
