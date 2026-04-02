package com.queuetable.shared.scheduling;

import com.queuetable.config.domain.RestaurantConfig;
import com.queuetable.config.domain.RestaurantConfigRepository;
import com.queuetable.queue.domain.QueueEntry;
import com.queuetable.queue.domain.QueueEntryRepository;
import com.queuetable.queue.domain.QueueEntryStatus;
import com.queuetable.queue.domain.QueueSseService;
import com.queuetable.queue.dto.PublicQueueEntryResponse;
import com.queuetable.queue.dto.QueueEntryResponse;
import com.queuetable.reservation.domain.Reservation;
import com.queuetable.reservation.domain.ReservationRepository;
import com.queuetable.reservation.domain.ReservationStatus;
import com.queuetable.reservation.dto.ReservationResponse;
import com.queuetable.shared.websocket.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(ExpirationJob.class);

    private final QueueEntryRepository queueEntryRepository;
    private final ReservationRepository reservationRepository;
    private final RestaurantConfigRepository configRepository;
    private final EventPublisher eventPublisher;
    private final QueueSseService queueSseService;

    public ExpirationJob(QueueEntryRepository queueEntryRepository,
                         ReservationRepository reservationRepository,
                         RestaurantConfigRepository configRepository,
                         EventPublisher eventPublisher,
                         QueueSseService queueSseService) {
        this.queueEntryRepository = queueEntryRepository;
        this.reservationRepository = reservationRepository;
        this.configRepository = configRepository;
        this.eventPublisher = eventPublisher;
        this.queueSseService = queueSseService;
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void processExpirations() {
        expireUnconfirmedQueueEntries();
        expireNoShowReservations();
    }

    private void expireUnconfirmedQueueEntries() {
        Map<UUID, RestaurantConfig> configCache = configRepository.findAll().stream()
                .collect(Collectors.toMap(RestaurantConfig::getRestaurantId, c -> c));

        // Find all NOTIFIED entries — filter by per-restaurant timeout
        List<QueueEntry> notified = queueEntryRepository
                .findByStatusAndNotifiedAtBefore(QueueEntryStatus.NOTIFIED, Instant.now());

        for (QueueEntry entry : notified) {
            RestaurantConfig config = configCache.get(entry.getRestaurantId());
            if (config == null) continue;

            Instant cutoff = entry.getNotifiedAt()
                    .plus(config.getConfirmationTimeoutMinutes(), ChronoUnit.MINUTES);

            if (Instant.now().isAfter(cutoff)) {
                entry.setStatus(QueueEntryStatus.EXPIRED);
                queueEntryRepository.save(entry);

                log.info("action=queue_entry_expired entryId={} restaurantId={} customerName={}",
                        entry.getId(), entry.getRestaurantId(), entry.getCustomerName());

                eventPublisher.publishQueueUpdated(entry.getRestaurantId(), QueueEntryResponse.from(entry));
                queueSseService.notifyEntry(entry.getId(), PublicQueueEntryResponse.from(entry));
            }
        }
    }

    private void expireNoShowReservations() {
        Map<UUID, RestaurantConfig> configCache = configRepository.findAll().stream()
                .collect(Collectors.toMap(RestaurantConfig::getRestaurantId, c -> c));

        List<Reservation> booked = reservationRepository
                .findByStatusAndReservedAtBefore(ReservationStatus.BOOKED, Instant.now());

        for (Reservation reservation : booked) {
            RestaurantConfig config = configCache.get(reservation.getRestaurantId());
            if (config == null) continue;

            Instant cutoff = reservation.getReservedAt()
                    .plus(config.getNoshowGraceMinutes(), ChronoUnit.MINUTES);

            if (Instant.now().isAfter(cutoff)) {
                reservation.setStatus(ReservationStatus.NO_SHOW);
                reservationRepository.save(reservation);

                log.info("action=reservation_no_show reservationId={} restaurantId={} customerName={}",
                        reservation.getId(), reservation.getRestaurantId(), reservation.getCustomerName());

                eventPublisher.publishReservationUpdated(
                        reservation.getRestaurantId(), ReservationResponse.from(reservation));
            }
        }
    }
}
