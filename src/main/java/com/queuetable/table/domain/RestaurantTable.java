package com.queuetable.table.domain;

import com.queuetable.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_tables")
public class RestaurantTable extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TableStatus status = TableStatus.FREE;

    @Column(length = 100)
    private String zone;

    @Column(name = "occupied_at")
    private Instant occupiedAt;

    @Column(name = "cleaning_started_at")
    private Instant cleaningStartedAt;

    protected RestaurantTable() {}

    public RestaurantTable(UUID restaurantId, String label, int capacity) {
        this.restaurantId = restaurantId;
        this.label = label;
        this.capacity = capacity;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public TableStatus getStatus() { return status; }
    public void setStatus(TableStatus status) { this.status = status; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public Instant getOccupiedAt() { return occupiedAt; }
    public void setOccupiedAt(Instant occupiedAt) { this.occupiedAt = occupiedAt; }
    public Instant getCleaningStartedAt() { return cleaningStartedAt; }
    public void setCleaningStartedAt(Instant cleaningStartedAt) { this.cleaningStartedAt = cleaningStartedAt; }
}
