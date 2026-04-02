package com.queuetable.queue.dto;

import com.queuetable.queue.domain.QueueEntry;
import com.queuetable.queue.domain.QueueEntryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Entrada en la cola (vista staff)")
public record QueueEntryResponse(
        UUID id,
        UUID restaurantId,
        String customerName,
        String customerPhone,
        int partySize,
        int position,
        QueueEntryStatus status,
        Integer estimatedWaitMinutes,
        Instant notifiedAt,
        boolean walkIn,
        Instant createdAt,
        Instant updatedAt
) {
    public static QueueEntryResponse from(QueueEntry e) {
        return new QueueEntryResponse(
                e.getId(), e.getRestaurantId(),
                e.getCustomerName(), e.getCustomerPhone(),
                e.getPartySize(), e.getPosition(),
                e.getStatus(), e.getEstimatedWaitMinutes(),
                e.getNotifiedAt(), e.isWalkIn(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
