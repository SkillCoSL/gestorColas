package com.queuetable.queue.domain;

import com.queuetable.config.domain.RestaurantConfig;
import com.queuetable.config.domain.RestaurantConfigRepository;
import com.queuetable.queue.dto.*;
import com.queuetable.restaurant.domain.Restaurant;
import com.queuetable.restaurant.domain.RestaurantRepository;
import com.queuetable.shared.event.QueueRecalculationEvent;
import com.queuetable.shared.exception.BadRequestException;
import com.queuetable.shared.exception.ForbiddenException;
import com.queuetable.shared.exception.ResourceNotFoundException;
import com.queuetable.shared.security.SecurityContextUtil;
import com.queuetable.shared.websocket.EventPublisher;
import org.springframework.context.event.EventListener;
import com.queuetable.table.domain.RestaurantTable;
import com.queuetable.table.domain.TableRepository;
import com.queuetable.table.domain.TableStatus;
import com.queuetable.table.dto.TableResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class QueueService {

    private final QueueEntryRepository queueEntryRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantConfigRepository configRepository;
    private final TableRepository tableRepository;
    private final EventPublisher eventPublisher;
    private final QueueSseService queueSseService;

    public QueueService(QueueEntryRepository queueEntryRepository,
                        RestaurantRepository restaurantRepository,
                        RestaurantConfigRepository configRepository,
                        TableRepository tableRepository,
                        EventPublisher eventPublisher,
                        QueueSseService queueSseService) {
        this.queueEntryRepository = queueEntryRepository;
        this.restaurantRepository = restaurantRepository;
        this.configRepository = configRepository;
        this.tableRepository = tableRepository;
        this.eventPublisher = eventPublisher;
        this.queueSseService = queueSseService;
    }

    @Transactional(readOnly = true)
    public PublicRestaurantResponse getPublicRestaurantInfo(String slug) {
        Restaurant restaurant = findActiveRestaurantBySlug(slug);
        return PublicRestaurantResponse.from(restaurant);
    }

    @Transactional(readOnly = true)
    public QueueStatusResponse getQueueStatus(String slug) {
        Restaurant restaurant = findActiveRestaurantBySlug(slug);
        RestaurantConfig config = getConfig(restaurant.getId());

        int waitingCount = queueEntryRepository.countWaiting(restaurant.getId());
        int estimatedWait = waitingCount * config.getAvgTableDurationMinutes();
        boolean queueOpen = config.getMaxQueueSize() == null || waitingCount < config.getMaxQueueSize();

        return new QueueStatusResponse(waitingCount, estimatedWait, queueOpen);
    }

    @Transactional
    public JoinQueueResponse joinQueue(String slug, JoinQueueRequest request) {
        Restaurant restaurant = findActiveRestaurantBySlug(slug);
        RestaurantConfig config = getConfig(restaurant.getId());

        int waitingCount = queueEntryRepository.countWaiting(restaurant.getId());
        if (config.getMaxQueueSize() != null && waitingCount >= config.getMaxQueueSize()) {
            throw new BadRequestException("Queue is full");
        }

        int nextPosition = queueEntryRepository.findMaxPosition(restaurant.getId()) + 1;

        QueueEntry entry = new QueueEntry(
                restaurant.getId(),
                request.customerName(),
                request.partySize(),
                nextPosition
        );
        if (request.customerPhone() != null) {
            entry.setCustomerPhone(request.customerPhone());
        }

        int estimatedWait = waitingCount * config.getAvgTableDurationMinutes();
        entry.setEstimatedWaitMinutes(estimatedWait);

        entry = queueEntryRepository.save(entry);

        eventPublisher.publishQueueUpdated(restaurant.getId(), QueueEntryResponse.from(entry));

        return new JoinQueueResponse(
                entry.getId(),
                entry.getAccessToken(),
                entry.getPosition(),
                estimatedWait
        );
    }

    @Transactional(readOnly = true)
    public PublicQueueEntryResponse getEntryStatus(UUID entryId, UUID accessToken) {
        QueueEntry entry = findByIdAndToken(entryId, accessToken);
        if (entry.getStatus().isActive()) {
            recalculateEntryPosition(entry);
        }
        return PublicQueueEntryResponse.from(entry);
    }

    @Transactional
    public void cancelEntryByCustomer(UUID entryId, UUID accessToken) {
        QueueEntry entry = findByIdAndToken(entryId, accessToken);
        cancelEntry(entry);
    }

    @Transactional(readOnly = true)
    public List<QueueEntryResponse> listQueue(UUID restaurantId, QueueEntryStatus status) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);

        List<QueueEntry> entries;
        if (status != null) {
            entries = queueEntryRepository.findByRestaurantIdAndStatusOrderByPositionAsc(restaurantId, status);
        } else {
            entries = queueEntryRepository.findByRestaurantIdAndStatusInOrderByPositionAsc(
                    restaurantId,
                    List.of(QueueEntryStatus.WAITING, QueueEntryStatus.NOTIFIED)
            );
        }
        return entries.stream().map(QueueEntryResponse::from).toList();
    }

    @Transactional
    public void cancelEntryByStaff(UUID restaurantId, UUID entryId) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);
        QueueEntry entry = findStaffEntry(restaurantId, entryId);
        cancelEntry(entry);
    }

    @Transactional
    public QueueEntryResponse seatEntry(UUID restaurantId, UUID entryId, UUID tableId) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);

        QueueEntry entry = findStaffEntry(restaurantId, entryId);
        if (!entry.getStatus().canTransitionTo(QueueEntryStatus.SEATED)) {
            throw new BadRequestException("Cannot seat entry with status: " + entry.getStatus());
        }

        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));
        if (!table.getRestaurantId().equals(restaurantId)) {
            throw new ForbiddenException("Table does not belong to this restaurant");
        }
        if (table.getStatus() != TableStatus.FREE) {
            throw new BadRequestException("Table is not free, current status: " + table.getStatus());
        }
        if (table.getCapacity() < entry.getPartySize()) {
            throw new BadRequestException(
                    "Table capacity (" + table.getCapacity() + ") is less than party size (" + entry.getPartySize() + ")");
        }

        entry.setStatus(QueueEntryStatus.SEATED);
        entry.setTableId(table.getId());
        table.setStatus(TableStatus.OCCUPIED);

        tableRepository.save(table);
        QueueEntry saved = queueEntryRepository.save(entry);
        recalculatePositions(restaurantId);

        eventPublisher.publishQueueUpdated(restaurantId, QueueEntryResponse.from(saved));
        eventPublisher.publishTableUpdated(restaurantId, TableResponse.from(table));
        queueSseService.notifyEntry(saved.getId(), PublicQueueEntryResponse.from(saved));

        return QueueEntryResponse.from(saved);
    }

    @Transactional
    public QueueEntryResponse addWalkIn(UUID restaurantId, JoinQueueRequest request) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);
        RestaurantConfig config = getConfig(restaurantId);

        int waitingCount = queueEntryRepository.countWaiting(restaurantId);
        int nextPosition = queueEntryRepository.findMaxPosition(restaurantId) + 1;

        QueueEntry entry = new QueueEntry(restaurantId, request.customerName(), request.partySize(), nextPosition);
        entry.setWalkIn(true);
        if (request.customerPhone() != null) {
            entry.setCustomerPhone(request.customerPhone());
        }
        entry.setEstimatedWaitMinutes(waitingCount * config.getAvgTableDurationMinutes());

        entry = queueEntryRepository.save(entry);
        eventPublisher.publishQueueUpdated(restaurantId, QueueEntryResponse.from(entry));
        return QueueEntryResponse.from(entry);
    }

    @Transactional
    public QueueEntryResponse notifyEntry(UUID restaurantId, UUID entryId) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);

        QueueEntry entry = findStaffEntry(restaurantId, entryId);
        if (!entry.getStatus().canTransitionTo(QueueEntryStatus.NOTIFIED)) {
            throw new BadRequestException("Cannot notify entry with status: " + entry.getStatus());
        }

        entry.setStatus(QueueEntryStatus.NOTIFIED);
        entry.setNotifiedAt(Instant.now());
        QueueEntry saved = queueEntryRepository.save(entry);

        eventPublisher.publishQueueUpdated(restaurantId, QueueEntryResponse.from(saved));
        queueSseService.notifyEntry(saved.getId(), PublicQueueEntryResponse.from(saved));

        return QueueEntryResponse.from(saved);
    }

    @Transactional
    public PublicQueueEntryResponse confirmEntry(UUID entryId, UUID accessToken) {
        QueueEntry entry = findByIdAndToken(entryId, accessToken);

        if (entry.getStatus() != QueueEntryStatus.NOTIFIED) {
            throw new BadRequestException("Can only confirm entries in NOTIFIED status");
        }

        // notifiedAt is already set; confirmation is implicit acknowledgment
        // Entry stays NOTIFIED until staff seats them
        // We publish the event so the staff panel sees the confirmation
        eventPublisher.publishQueueUpdated(entry.getRestaurantId(), QueueEntryResponse.from(entry));
        return PublicQueueEntryResponse.from(entry);
    }

    @Transactional
    public QueueEntryResponse skipEntry(UUID restaurantId, UUID entryId) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);

        QueueEntry entry = findStaffEntry(restaurantId, entryId);
        if (!entry.getStatus().isActive()) {
            throw new BadRequestException("Cannot skip entry with status: " + entry.getStatus());
        }

        // Move to end of queue
        int maxPosition = queueEntryRepository.findMaxPosition(restaurantId);
        entry.setPosition(maxPosition + 1);
        entry.setStatus(QueueEntryStatus.WAITING);
        entry.setNotifiedAt(null);

        QueueEntry saved = queueEntryRepository.save(entry);
        recalculatePositions(restaurantId);

        eventPublisher.publishQueueUpdated(restaurantId, QueueEntryResponse.from(saved));
        queueSseService.notifyEntry(saved.getId(), PublicQueueEntryResponse.from(saved));

        return QueueEntryResponse.from(saved);
    }

    @EventListener
    @Transactional
    public void onQueueRecalculation(QueueRecalculationEvent event) {
        recalculatePositions(event.restaurantId());
    }

    private QueueEntry findStaffEntry(UUID restaurantId, UUID entryId) {
        QueueEntry entry = queueEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", entryId));
        if (!entry.getRestaurantId().equals(restaurantId)) {
            throw new ForbiddenException("Entry does not belong to this restaurant");
        }
        return entry;
    }

    private void cancelEntry(QueueEntry entry) {
        if (!entry.getStatus().canTransitionTo(QueueEntryStatus.CANCELLED)) {
            throw new BadRequestException(
                    "Cannot cancel entry with status: " + entry.getStatus());
        }
        entry.setStatus(QueueEntryStatus.CANCELLED);
        queueEntryRepository.save(entry);
        recalculatePositions(entry.getRestaurantId());
        eventPublisher.publishQueueUpdated(entry.getRestaurantId(), QueueEntryResponse.from(entry));
        queueSseService.notifyEntry(entry.getId(), PublicQueueEntryResponse.from(entry));
    }

    private void recalculatePositions(UUID restaurantId) {
        RestaurantConfig config = getConfig(restaurantId);
        List<QueueEntry> activeEntries = queueEntryRepository
                .findByRestaurantIdAndStatusOrderByPositionAsc(restaurantId, QueueEntryStatus.WAITING);

        int pos = 1;
        for (QueueEntry e : activeEntries) {
            e.setPosition(pos);
            e.setEstimatedWaitMinutes((pos - 1) * config.getAvgTableDurationMinutes());
            pos++;
        }
        queueEntryRepository.saveAll(activeEntries);
        queueSseService.notifyRestaurantQueue(restaurantId);
    }

    private void recalculateEntryPosition(QueueEntry entry) {
        if (entry.getStatus() != QueueEntryStatus.WAITING) return;

        RestaurantConfig config = getConfig(entry.getRestaurantId());
        List<QueueEntry> waitingEntries = queueEntryRepository
                .findByRestaurantIdAndStatusOrderByPositionAsc(entry.getRestaurantId(), QueueEntryStatus.WAITING);

        int pos = 1;
        for (QueueEntry e : waitingEntries) {
            if (e.getId().equals(entry.getId())) {
                entry.setPosition(pos);
                entry.setEstimatedWaitMinutes((pos - 1) * config.getAvgTableDurationMinutes());
                break;
            }
            pos++;
        }
    }

    private Restaurant findActiveRestaurantBySlug(String slug) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", slug));
        if (!restaurant.isActive()) {
            throw new BadRequestException("Restaurant is not active");
        }
        return restaurant;
    }

    private RestaurantConfig getConfig(UUID restaurantId) {
        return configRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("RestaurantConfig", restaurantId));
    }

    private QueueEntry findByIdAndToken(UUID entryId, UUID accessToken) {
        return queueEntryRepository.findByIdAndAccessToken(entryId, accessToken)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", entryId));
    }
}
