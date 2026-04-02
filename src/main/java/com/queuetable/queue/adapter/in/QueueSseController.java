package com.queuetable.queue.adapter.in;

import com.queuetable.queue.domain.QueueSseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/public")
public class QueueSseController {

    private final QueueSseService queueSseService;

    public QueueSseController(QueueSseService queueSseService) {
        this.queueSseService = queueSseService;
    }

    @GetMapping(value = "/queue/{entryId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable UUID entryId,
                                @RequestParam UUID accessToken) {
        return queueSseService.subscribe(entryId, accessToken);
    }
}
