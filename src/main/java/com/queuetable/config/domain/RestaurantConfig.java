package com.queuetable.config.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "restaurant_configs")
public class RestaurantConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    private UUID restaurantId;

    @Column(name = "confirmation_timeout_minutes", nullable = false)
    private int confirmationTimeoutMinutes = 5;

    @Column(name = "noshow_grace_minutes", nullable = false)
    private int noshowGraceMinutes = 15;

    @Column(name = "avg_table_duration_minutes", nullable = false)
    private int avgTableDurationMinutes = 45;

    @Column(name = "reservation_protection_window_minutes", nullable = false)
    private int reservationProtectionWindowMinutes = 30;

    @Column(name = "max_queue_size")
    private Integer maxQueueSize;

    @Column(name = "cleaning_duration_minutes", nullable = false)
    private int cleaningDurationMinutes = 5;

    @Column(name = "wait_buffer_minutes", nullable = false)
    private int waitBufferMinutes = 3;

    protected RestaurantConfig() {}

    public static RestaurantConfig createDefault(UUID restaurantId) {
        RestaurantConfig config = new RestaurantConfig();
        config.restaurantId = restaurantId;
        return config;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public int getConfirmationTimeoutMinutes() { return confirmationTimeoutMinutes; }
    public void setConfirmationTimeoutMinutes(int value) { this.confirmationTimeoutMinutes = value; }
    public int getNoshowGraceMinutes() { return noshowGraceMinutes; }
    public void setNoshowGraceMinutes(int value) { this.noshowGraceMinutes = value; }
    public int getAvgTableDurationMinutes() { return avgTableDurationMinutes; }
    public void setAvgTableDurationMinutes(int value) { this.avgTableDurationMinutes = value; }
    public int getReservationProtectionWindowMinutes() { return reservationProtectionWindowMinutes; }
    public void setReservationProtectionWindowMinutes(int value) { this.reservationProtectionWindowMinutes = value; }
    public Integer getMaxQueueSize() { return maxQueueSize; }
    public void setMaxQueueSize(Integer value) { this.maxQueueSize = value; }
    public int getCleaningDurationMinutes() { return cleaningDurationMinutes; }
    public void setCleaningDurationMinutes(int value) { this.cleaningDurationMinutes = value; }
    public int getWaitBufferMinutes() { return waitBufferMinutes; }
    public void setWaitBufferMinutes(int value) { this.waitBufferMinutes = value; }
}
