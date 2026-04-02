package com.queuetable.queue.domain;

import com.queuetable.config.domain.RestaurantConfig;
import com.queuetable.config.domain.RestaurantConfigRepository;
import com.queuetable.queue.dto.*;
import com.queuetable.restaurant.domain.Restaurant;
import com.queuetable.restaurant.domain.RestaurantRepository;
import com.queuetable.shared.exception.BadRequestException;
import com.queuetable.shared.exception.ForbiddenException;
import com.queuetable.shared.exception.ResourceNotFoundException;
import com.queuetable.shared.security.SecurityContextUtil;
import com.queuetable.shared.websocket.EventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class QueueService {

    private final QueueEntryRepository queueEntryRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantConfigRepository configRepository;
    private final EventPublisher eventPublisher;

    public QueueService(QueueEntryRepository queueEntryRepository,
                        RestaurantRepository restaurantRepository,
                        RestaurantConfigRepository configRepository,
                        EventPublisher eventPublisher) {
        this.queueEntryRepository = queueEntryRepository;
        this.restaurantRepository = restaurantRepository;
        this.configRepository = configRepository;
        this.eventPublisher = eventPublisher;
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

        QueueEntry entry = queueEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", entryId));

        if (!entry.getRestaurantId().equals(restaurantId)) {
            throw new ForbiddenException("Entry does not belong to this restaurant");
        }

        cancelEntry(entry);
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
