package com.queuetable.table.domain;

import com.queuetable.config.domain.RestaurantConfig;
import com.queuetable.config.domain.RestaurantConfigRepository;
import com.queuetable.reservation.domain.Reservation;
import com.queuetable.reservation.domain.ReservationRepository;
import com.queuetable.reservation.domain.ReservationStatus;
import com.queuetable.shared.event.QueueRecalculationEvent;
import com.queuetable.shared.exception.BadRequestException;
import com.queuetable.shared.exception.ResourceNotFoundException;
import com.queuetable.shared.security.SecurityContextUtil;
import com.queuetable.shared.websocket.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import com.queuetable.table.dto.CreateTableRequest;
import com.queuetable.table.dto.CreateTablesBulkRequest;
import com.queuetable.table.dto.TableResponse;
import com.queuetable.table.dto.UpdateTableRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TableService {

    private static final Logger log = LoggerFactory.getLogger(TableService.class);
    private static final int MAX_BULK_TABLES = 200;
    private static final int MAX_LABEL_LENGTH = 100;
    private static final int PENDING_RESERVATION_WARNING_MINUTES = 60;

    private final TableRepository tableRepository;
    private final ReservationRepository reservationRepository;
    private final RestaurantConfigRepository configRepository;
    private final EventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public TableService(TableRepository tableRepository,
                        ReservationRepository reservationRepository,
                        RestaurantConfigRepository configRepository,
                        EventPublisher eventPublisher,
                        ApplicationEventPublisher applicationEventPublisher) {
        this.tableRepository = tableRepository;
        this.reservationRepository = reservationRepository;
        this.configRepository = configRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<TableResponse> listByRestaurant(UUID restaurantId) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);
        List<RestaurantTable> tables = tableRepository.findByRestaurantIdOrderByLabelAsc(restaurantId);
        Set<UUID> reservedSoonTableIds = getReservationWarningTableIds(restaurantId);
        return tables.stream()
                .map(t -> TableResponse.from(t, reservedSoonTableIds.contains(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getAvailableTables(UUID restaurantId, int groupSize) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);
        List<RestaurantTable> tables = tableRepository.findByRestaurantIdOrderByLabelAsc(restaurantId);
        Set<UUID> reservedSoonTableIds = getProtectedReservedTableIds(restaurantId);

        return tables.stream()
                .filter(t -> t.getStatus() == TableStatus.FREE)
                .filter(t -> t.getCapacity() >= groupSize)
                .filter(t -> !reservedSoonTableIds.contains(t.getId()))
                .map(t -> TableResponse.from(t, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<UUID> getProtectedReservedTableIds(UUID restaurantId) {
        RestaurantConfig config = configRepository.findByRestaurantId(restaurantId).orElse(null);
        if (config == null) return Set.of();

        Instant now = Instant.now();
        Instant windowEnd = now.plus(config.getReservationProtectionWindowMinutes(), ChronoUnit.MINUTES);

        List<Reservation> upcoming = reservationRepository
                .findByRestaurantIdAndStatusAndReservedAtBetweenOrderByReservedAtAsc(
                        restaurantId, ReservationStatus.BOOKED, now, windowEnd);

        return upcoming.stream()
                .map(Reservation::getTableId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
    }

    private Set<UUID> getReservationWarningTableIds(UUID restaurantId) {
        Instant now = Instant.now();
        Instant warningWindowEnd = now.plus(PENDING_RESERVATION_WARNING_MINUTES, ChronoUnit.MINUTES);

        return reservationRepository.findByRestaurantIdOrderByReservedAtAsc(restaurantId).stream()
                .filter(reservation -> reservation.getTableId() != null)
                .filter(reservation -> {
                    if (reservation.getStatus() == ReservationStatus.BOOKED) {
                        return !reservation.getReservedAt().isBefore(now)
                                && !reservation.getReservedAt().isAfter(warningWindowEnd);
                    }
                    return reservation.getStatus() == ReservationStatus.ARRIVED
                            && !reservation.getReservedAt().isAfter(warningWindowEnd);
                })
                .map(Reservation::getTableId)
                .collect(Collectors.toSet());
    }

    @Transactional
    public RestaurantTable create(UUID restaurantId, CreateTableRequest request) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);

        if (tableRepository.existsByRestaurantIdAndLabel(restaurantId, request.label())) {
            throw new BadRequestException("Table label already exists: " + request.label());
        }

        RestaurantTable table = new RestaurantTable(restaurantId, request.label(), request.capacity());
        if (request.zone() != null) {
            table.setZone(request.zone());
        }
        return tableRepository.save(table);
    }

    @Transactional
    public List<RestaurantTable> createBulk(UUID restaurantId, CreateTablesBulkRequest request) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);

        if (request.fromNumber() > request.toNumber()) {
            throw new BadRequestException("fromNumber must be less than or equal to toNumber");
        }

        int totalTables = request.toNumber() - request.fromNumber() + 1;
        if (totalTables > MAX_BULK_TABLES) {
            throw new BadRequestException("Cannot create more than " + MAX_BULK_TABLES + " tables at once");
        }

        String prefix = request.labelPrefix().trim();
        Set<String> existingLabels = tableRepository.findByRestaurantIdOrderByLabelAsc(restaurantId).stream()
                .map(RestaurantTable::getLabel)
                .collect(Collectors.toSet());
        Set<String> batchLabels = new HashSet<>();

        for (int current = request.fromNumber(); current <= request.toNumber(); current++) {
            String generatedLabel = buildGeneratedLabel(prefix, current);
            if (!batchLabels.add(generatedLabel) || existingLabels.contains(generatedLabel)) {
                throw new BadRequestException("Table label already exists: " + generatedLabel);
            }
        }

        List<RestaurantTable> tablesToCreate = new ArrayList<>(totalTables);
        for (int current = request.fromNumber(); current <= request.toNumber(); current++) {
            RestaurantTable table = new RestaurantTable(
                    restaurantId,
                    buildGeneratedLabel(prefix, current),
                    request.capacity()
            );
            if (request.zone() != null) {
                table.setZone(request.zone());
            }
            tablesToCreate.add(table);
        }

        return tableRepository.saveAll(tablesToCreate);
    }

    @Transactional
    public RestaurantTable update(UUID tableId, UpdateTableRequest request) {
        RestaurantTable table = findAndValidateOwnership(tableId);

        if (request.label() != null) {
            if (!request.label().equals(table.getLabel())
                    && tableRepository.existsByRestaurantIdAndLabel(table.getRestaurantId(), request.label())) {
                throw new BadRequestException("Table label already exists: " + request.label());
            }
            table.setLabel(request.label());
        }
        if (request.capacity() != null) table.setCapacity(request.capacity());
        if (request.zone() != null) table.setZone(request.zone());

        return tableRepository.save(table);
    }

    @Transactional
    public RestaurantTable updateStatus(UUID tableId, TableStatus newStatus) {
        RestaurantTable table = findAndValidateOwnership(tableId);

        if (!table.getStatus().canTransitionTo(newStatus)) {
            throw new BadRequestException(
                    "Invalid transition: " + table.getStatus() + " → " + newStatus);
        }

        TableStatus fromStatus = table.getStatus();
        Instant now = Instant.now();
        table.setStatus(newStatus);
        switch (newStatus) {
            case OCCUPIED -> {
                table.setOccupiedAt(now);
                table.setCleaningStartedAt(null);
            }
            case CLEANING -> {
                table.setCleaningStartedAt(now);
                table.setOccupiedAt(null);
            }
            case FREE -> {
                table.setOccupiedAt(null);
                table.setCleaningStartedAt(null);
            }
        }
        RestaurantTable saved = tableRepository.save(table);

        log.info("action=table_status_change tableId={} restaurantId={} from={} to={} label={}",
                saved.getId(), saved.getRestaurantId(), fromStatus, newStatus, saved.getLabel());

        eventPublisher.publishTableUpdated(saved.getRestaurantId(), TableResponse.from(saved));
        applicationEventPublisher.publishEvent(new QueueRecalculationEvent(saved.getRestaurantId()));

        return saved;
    }

    @Transactional
    public void delete(UUID tableId) {
        RestaurantTable table = findAndValidateOwnership(tableId);

        if (table.getStatus() != TableStatus.FREE) {
            throw new BadRequestException("Cannot delete table with status: " + table.getStatus());
        }

        tableRepository.delete(table);
    }

    private RestaurantTable findAndValidateOwnership(UUID tableId) {
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));
        SecurityContextUtil.validateRestaurantOwnership(table.getRestaurantId());
        return table;
    }

    private String buildGeneratedLabel(String prefix, int number) {
        String label = (prefix + " " + number).trim();
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new BadRequestException("Generated label exceeds 100 characters: " + label);
        }
        return label;
    }
}
