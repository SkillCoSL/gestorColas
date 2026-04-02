package com.queuetable.table.adapter.in;

import com.queuetable.table.domain.TableService;
import com.queuetable.table.dto.CreateTableRequest;
import com.queuetable.table.dto.TableResponse;
import com.queuetable.table.dto.UpdateTableRequest;
import com.queuetable.table.dto.UpdateTableStatusRequest;
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
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/restaurants/{restaurantId}/tables")
    public ResponseEntity<List<TableResponse>> list(@PathVariable UUID restaurantId) {
        return ResponseEntity.ok(tableService.listByRestaurant(restaurantId));
    }

    @GetMapping("/restaurants/{restaurantId}/tables/available")
    public ResponseEntity<List<TableResponse>> available(
            @PathVariable UUID restaurantId,
            @RequestParam int groupSize) {
        return ResponseEntity.ok(tableService.getAvailableTables(restaurantId, groupSize));
    }

    @PostMapping("/restaurants/{restaurantId}/tables")
    public ResponseEntity<TableResponse> create(@PathVariable UUID restaurantId,
                                                @Valid @RequestBody CreateTableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TableResponse.from(tableService.create(restaurantId, request)));
    }

    @PatchMapping("/tables/{id}")
    public ResponseEntity<TableResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateTableRequest request) {
        return ResponseEntity.ok(TableResponse.from(tableService.update(id, request)));
    }

    @PatchMapping("/tables/{id}/status")
    public ResponseEntity<TableResponse> updateStatus(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateTableStatusRequest request) {
        return ResponseEntity.ok(TableResponse.from(tableService.updateStatus(id, request.status())));
    }

    @DeleteMapping("/tables/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        tableService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
