package com.queuetable.queue.domain;

import com.queuetable.queue.dto.PublicQueueEntryResponse;
import com.queuetable.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class QueueSseService {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5 minutes

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final QueueEntryRepository queueEntryRepository;

    public QueueSseService(QueueEntryRepository queueEntryRepository) {
        this.queueEntryRepository = queueEntryRepository;
    }

    public SseEmitter subscribe(UUID entryId, UUID accessToken) {
        QueueEntry entry = queueEntryRepository.findByIdAndAccessToken(entryId, accessToken)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", entryId));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitters.computeIfAbsent(entryId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(entryId, emitter));
        emitter.onTimeout(() -> removeEmitter(entryId, emitter));
        emitter.onError(e -> removeEmitter(entryId, emitter));

        // Send initial state
        try {
            emitter.send(SseEmitter.event()
                    .name("queue.status")
                    .data(PublicQueueEntryResponse.from(entry)));
        } catch (IOException e) {
            removeEmitter(entryId, emitter);
        }

        return emitter;
    }

    public void notifyEntry(UUID entryId, PublicQueueEntryResponse data) {
        List<SseEmitter> entryEmitters = emitters.get(entryId);
        if (entryEmitters == null) return;

        for (SseEmitter emitter : entryEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("queue.status")
                        .data(data));
            } catch (IOException e) {
                removeEmitter(entryId, emitter);
            }
        }
    }

    public void notifyRestaurantQueue(UUID restaurantId) {
        // Notify all active entries for this restaurant
        List<QueueEntry> activeEntries = queueEntryRepository
                .findByRestaurantIdAndStatusOrderByPositionAsc(restaurantId, QueueEntryStatus.WAITING);

        for (QueueEntry entry : activeEntries) {
            notifyEntry(entry.getId(), PublicQueueEntryResponse.from(entry));
        }
    }

    private void removeEmitter(UUID entryId, SseEmitter emitter) {
        List<SseEmitter> entryEmitters = emitters.get(entryId);
        if (entryEmitters != null) {
            entryEmitters.remove(emitter);
            if (entryEmitters.isEmpty()) {
                emitters.remove(entryId);
            }
        }
    }
}
