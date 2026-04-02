package com.queuetable.queue.adapter.in;

import com.queuetable.queue.domain.QueueSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/public")
@Tag(name = "Cola publica", description = "Endpoints publicos para clientes — sin autenticacion")
public class QueueSseController {

    private final QueueSseService queueSseService;

    public QueueSseController(QueueSseService queueSseService) {
        this.queueSseService = queueSseService;
    }

    @GetMapping(value = "/queue/{entryId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Suscribirse a eventos SSE de la entrada en cola", description = "Stream de Server-Sent Events con actualizaciones en tiempo real de posicion y estado")
    @ApiResponse(responseCode = "200", description = "Stream SSE abierto")
    @ApiResponse(responseCode = "403", description = "Access token invalido")
    @ApiResponse(responseCode = "404", description = "Entrada no encontrada")
    public SseEmitter subscribe(@PathVariable UUID entryId,
                                @Parameter(description = "Token de acceso recibido al unirse") @RequestParam UUID accessToken) {
        return queueSseService.subscribe(entryId, accessToken);
    }
}
