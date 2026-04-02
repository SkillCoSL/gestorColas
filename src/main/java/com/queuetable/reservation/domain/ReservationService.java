package com.queuetable.reservation.domain;

import com.queuetable.reservation.dto.CreateReservationRequest;
import com.queuetable.reservation.dto.ReservationResponse;
import com.queuetable.reservation.dto.SeatReservationRequest;
import com.queuetable.reservation.dto.UpdateReservationRequest;
import com.queuetable.shared.exception.BadRequestException;
import com.queuetable.shared.exception.ForbiddenException;
import com.queuetable.shared.exception.ResourceNotFoundException;
import com.queuetable.shared.security.SecurityContextUtil;
import com.queuetable.shared.websocket.EventPublisher;
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

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final EventPublisher eventPublisher;

    public ReservationService(ReservationRepository reservationRepository,
                              TableRepository tableRepository,
                              EventPublisher eventPublisher) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.eventPublisher = eventPublisher;
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

        if (request.customerName() != null) reservation.setCustomerName(request.customerName());
        if (request.customerPhone() != null) reservation.setCustomerPhone(request.customerPhone());
        if (request.partySize() != null) reservation.setPartySize(request.partySize());
        if (request.reservedAt() != null) reservation.setReservedAt(request.reservedAt());
        if (request.notes() != null) reservation.setNotes(request.notes());

        return ReservationResponse.from(reservationRepository.save(reservation));
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
        if (table.getCapacity() < reservation.getPartySize()) {
            throw new BadRequestException(
                    "Table capacity (" + table.getCapacity() + ") is less than party size (" + reservation.getPartySize() + ")");
        }

        reservation.setTableId(table.getId());
        table.setStatus(TableStatus.OCCUPIED);

        tableRepository.save(table);
        eventPublisher.publishTableUpdated(reservation.getRestaurantId(), TableResponse.from(table));
        return saveAndPublish(reservation);
    }

    @Transactional
    public ReservationResponse markNoShow(UUID reservationId) {
        Reservation reservation = findAndValidateOwnership(reservationId);
        transition(reservation, ReservationStatus.NO_SHOW);
        return saveAndPublish(reservation);
    }

    @Transactional
    public ReservationResponse cancel(UUID reservationId) {
        Reservation reservation = findAndValidateOwnership(reservationId);
        transition(reservation, ReservationStatus.CANCELLED);
        return saveAndPublish(reservation);
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
        reservation.setStatus(target);
    }

    private Reservation findAndValidateOwnership(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId));
        SecurityContextUtil.validateRestaurantOwnership(reservation.getRestaurantId());
        return reservation;
    }
}
