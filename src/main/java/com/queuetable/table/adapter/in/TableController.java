package com.queuetable.table.adapter.in;

import com.queuetable.table.domain.TableService;
import com.queuetable.table.dto.CreateTableRequest;
import com.queuetable.table.dto.TableResponse;
import com.queuetable.table.dto.UpdateTableRequest;
import com.queuetable.table.dto.UpdateTableStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Mesas", description = "CRUD de mesas y transiciones de estado (FREE, OCCUPIED, CLEANING)")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/restaurants/{restaurantId}/tables")
    @Operation(summary = "Listar mesas del restaurante")
    @ApiResponse(responseCode = "200", description = "Lista de mesas")
    public ResponseEntity<List<TableResponse>> list(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(tableService.listByRestaurant(restaurantId));
    }

    @GetMapping("/restaurants/{restaurantId}/tables/available")
    @Operation(summary = "Listar mesas disponibles por tamanio de grupo")
    @ApiResponse(responseCode = "200", description = "Lista de mesas disponibles")
    public ResponseEntity<List<TableResponse>> available(
            @PathVariable UUID restaurantId,
            @Parameter(description = "Tamanio del grupo a sentar") @RequestParam int groupSize) {
        return ResponseEntity.ok(tableService.getAvailableTables(restaurantId, groupSize));
    }

    @PostMapping("/restaurants/{restaurantId}/tables")
    @Operation(summary = "Crear nueva mesa")
    @ApiResponse(responseCode = "201", description = "Mesa creada")
    public ResponseEntity<TableResponse> create(@PathVariable UUID restaurantId,
                                                @Valid @RequestBody CreateTableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TableResponse.from(tableService.create(restaurantId, request)));
    }

    @PatchMapping("/tables/{id}")
    @Operation(summary = "Actualizar datos de una mesa")
    @ApiResponse(responseCode = "200", description = "Mesa actualizada")
    @ApiResponse(responseCode = "404", description = "Mesa no encontrada")
    public ResponseEntity<TableResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateTableRequest request) {
        return ResponseEntity.ok(TableResponse.from(tableService.update(id, request)));
    }

    @PatchMapping("/tables/{id}/status")
    @Operation(summary = "Cambiar estado de una mesa", description = "Transiciones validas: FREE → OCCUPIED → CLEANING → FREE")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "400", description = "Transicion de estado invalida")
    @ApiResponse(responseCode = "404", description = "Mesa no encontrada")
    public ResponseEntity<TableResponse> updateStatus(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateTableStatusRequest request) {
        return ResponseEntity.ok(TableResponse.from(tableService.updateStatus(id, request.status())));
    }

    @DeleteMapping("/tables/{id}")
    @Operation(summary = "Eliminar una mesa")
    @ApiResponse(responseCode = "204", description = "Mesa eliminada")
    @ApiResponse(responseCode = "404", description = "Mesa no encontrada")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        tableService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
