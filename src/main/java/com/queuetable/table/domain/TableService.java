package com.queuetable.table.domain;

import com.queuetable.shared.exception.BadRequestException;
import com.queuetable.shared.exception.ResourceNotFoundException;
import com.queuetable.shared.security.SecurityContextUtil;
import com.queuetable.shared.websocket.EventPublisher;
import com.queuetable.table.dto.CreateTableRequest;
import com.queuetable.table.dto.TableResponse;
import com.queuetable.table.dto.UpdateTableRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TableService {

    private final TableRepository tableRepository;
    private final EventPublisher eventPublisher;

    public TableService(TableRepository tableRepository, EventPublisher eventPublisher) {
        this.tableRepository = tableRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<RestaurantTable> listByRestaurant(UUID restaurantId) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);
        return tableRepository.findByRestaurantIdOrderByLabelAsc(restaurantId);
    }

    @Transactional
    public RestaurantTable create(UUID restaurantId, CreateTableRequest request) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);

        if (tableRepository.existsByRestaurantIdAndLabel(restaurantId, request.label())) {
            throw new BadRequestException("Table label already exists: " + request.label());
        }

        RestaurantTable table = new RestaurantTable(restaurantId, request.label(), request.capacity());
        if (request.zone() != null) {
            table.setZone(request.zone());
        }
        return tableRepository.save(table);
    }

    @Transactional
    public RestaurantTable update(UUID tableId, UpdateTableRequest request) {
        RestaurantTable table = findAndValidateOwnership(tableId);

        if (request.label() != null) {
            if (!request.label().equals(table.getLabel())
                    && tableRepository.existsByRestaurantIdAndLabel(table.getRestaurantId(), request.label())) {
                throw new BadRequestException("Table label already exists: " + request.label());
            }
            table.setLabel(request.label());
        }
        if (request.capacity() != null) table.setCapacity(request.capacity());
        if (request.zone() != null) table.setZone(request.zone());

        return tableRepository.save(table);
    }

    @Transactional
    public RestaurantTable updateStatus(UUID tableId, TableStatus newStatus) {
        RestaurantTable table = findAndValidateOwnership(tableId);

        if (!table.getStatus().canTransitionTo(newStatus)) {
            throw new BadRequestException(
                    "Invalid transition: " + table.getStatus() + " → " + newStatus);
        }

        table.setStatus(newStatus);
        RestaurantTable saved = tableRepository.save(table);
        eventPublisher.publishTableUpdated(saved.getRestaurantId(), TableResponse.from(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID tableId) {
        RestaurantTable table = findAndValidateOwnership(tableId);

        if (table.getStatus() != TableStatus.FREE) {
            throw new BadRequestException("Cannot delete table with status: " + table.getStatus());
        }

        tableRepository.delete(table);
    }

    private RestaurantTable findAndValidateOwnership(UUID tableId) {
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table", tableId));
        SecurityContextUtil.validateRestaurantOwnership(table.getRestaurantId());
        return table;
    }
}
