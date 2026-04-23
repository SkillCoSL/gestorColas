package com.queuetable.config.domain;

import com.queuetable.config.dto.RestaurantConfigUpdateRequest;
import com.queuetable.shared.exception.ResourceNotFoundException;
import com.queuetable.shared.security.SecurityContextUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class RestaurantConfigService {

    private final RestaurantConfigRepository configRepository;

    public RestaurantConfigService(RestaurantConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Transactional(readOnly = true)
    public RestaurantConfig getByRestaurantId(UUID restaurantId) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);
        return configRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("RestaurantConfig", restaurantId));
    }

    @Transactional
    public RestaurantConfig update(UUID restaurantId, RestaurantConfigUpdateRequest request) {
        SecurityContextUtil.validateRestaurantOwnership(restaurantId);
        RestaurantConfig config = configRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("RestaurantConfig", restaurantId));

        if (request.confirmationTimeoutMinutes() != null)
            config.setConfirmationTimeoutMinutes(request.confirmationTimeoutMinutes());
        if (request.noshowGraceMinutes() != null)
            config.setNoshowGraceMinutes(request.noshowGraceMinutes());
        if (request.avgTableDurationMinutes() != null)
            config.setAvgTableDurationMinutes(request.avgTableDurationMinutes());
        if (request.reservationProtectionWindowMinutes() != null)
            config.setReservationProtectionWindowMinutes(request.reservationProtectionWindowMinutes());
        if (request.maxQueueSize() != null)
            config.setMaxQueueSize(request.maxQueueSize());
        if (request.cleaningDurationMinutes() != null)
            config.setCleaningDurationMinutes(request.cleaningDurationMinutes());
        if (request.waitBufferMinutes() != null)
            config.setWaitBufferMinutes(request.waitBufferMinutes());

        return configRepository.save(config);
    }
}
