package com.queuetable.shared.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public EventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishQueueUpdated(UUID restaurantId, Object payload) {
        messagingTemplate.convertAndSend(
                "/topic/restaurant/" + restaurantId + "/queue.updated", payload);
    }

    public void publishTableUpdated(UUID restaurantId, Object payload) {
        messagingTemplate.convertAndSend(
                "/topic/restaurant/" + restaurantId + "/table.updated", payload);
    }

    public void publishReservationUpdated(UUID restaurantId, Object payload) {
        messagingTemplate.convertAndSend(
                "/topic/restaurant/" + restaurantId + "/reservation.updated", payload);
    }
}
