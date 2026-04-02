package com.queuetable.queue.adapter.in;

import com.queuetable.queue.domain.QueueEntryStatus;
import com.queuetable.queue.domain.QueueService;
import com.queuetable.queue.dto.JoinQueueRequest;
import com.queuetable.queue.dto.QueueEntryResponse;
import com.queuetable.queue.dto.SeatQueueEntryRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class StaffQueueController {

    private final QueueService queueService;

    public StaffQueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/restaurants/{restaurantId}/queue")
    public ResponseEntity<List<QueueEntryResponse>> listQueue(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) QueueEntryStatus status) {
        return ResponseEntity.ok(queueService.listQueue(restaurantId, status));
    }

    @PostMapping("/restaurants/{restaurantId}/queue")
    public ResponseEntity<QueueEntryResponse> addWalkIn(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody JoinQueueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(queueService.addWalkIn(restaurantId, request));
    }

    @PostMapping("/restaurants/{restaurantId}/queue/{entryId}/seat")
    public ResponseEntity<QueueEntryResponse> seatEntry(
            @PathVariable UUID restaurantId,
            @PathVariable UUID entryId,
            @Valid @RequestBody SeatQueueEntryRequest request) {
        return ResponseEntity.ok(queueService.seatEntry(restaurantId, entryId, request.tableId()));
    }

    @PostMapping("/restaurants/{restaurantId}/queue/{entryId}/cancel")
    public ResponseEntity<Void> cancelEntry(@PathVariable UUID restaurantId,
                                            @PathVariable UUID entryId) {
        queueService.cancelEntryByStaff(restaurantId, entryId);
        return ResponseEntity.noContent().build();
    }
}
