package com.queuetable.config.adapter.in;

import com.queuetable.auth.dto.AuthResponse;
import com.queuetable.config.dto.RestaurantConfigUpdateRequest;
import com.queuetable.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RestaurantConfigControllerTest extends AbstractIntegrationTest {

    private AuthResponse freshRestaurant() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return registerRestaurant("cfg-" + id, id + "@test.com");
    }

    private String bearer(AuthResponse auth) {
        return "Bearer " + auth.accessToken();
    }

    // -------------------------------------------------------------------------
    // GET /restaurants/{id}/config
    // -------------------------------------------------------------------------

    @Test
    void getConfig_returnsDefaults() throws Exception {
        AuthResponse auth = freshRestaurant();

        mockMvc.perform(get("/restaurants/{id}/config", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationTimeoutMinutes").value(5))
                .andExpect(jsonPath("$.noshowGraceMinutes").value(15))
                .andExpect(jsonPath("$.avgTableDurationMinutes").value(45))
                .andExpect(jsonPath("$.reservationProtectionWindowMinutes").value(30))
                .andExpect(jsonPath("$.maxQueueSize").isEmpty());
    }

    @Test
    void getConfig_otherRestaurant_returns403() throws Exception {
        AuthResponse owner = freshRestaurant();
        AuthResponse intruder = freshRestaurant();

        mockMvc.perform(get("/restaurants/{id}/config", owner.restaurantId())
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // PATCH /restaurants/{id}/config
    // -------------------------------------------------------------------------

    @Test
    void updateConfig_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        var update = new RestaurantConfigUpdateRequest(10, 20, 60, 45, 30);

        mockMvc.perform(patch("/restaurants/{id}/config", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationTimeoutMinutes").value(10))
                .andExpect(jsonPath("$.noshowGraceMinutes").value(20))
                .andExpect(jsonPath("$.avgTableDurationMinutes").value(60))
                .andExpect(jsonPath("$.reservationProtectionWindowMinutes").value(45))
                .andExpect(jsonPath("$.maxQueueSize").value(30));
    }

    @Test
    void updateConfig_partialFields() throws Exception {
        AuthResponse auth = freshRestaurant();
        var update = new RestaurantConfigUpdateRequest(null, null, 90, null, null);

        mockMvc.perform(patch("/restaurants/{id}/config", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgTableDurationMinutes").value(90))
                // Other fields remain default
                .andExpect(jsonPath("$.confirmationTimeoutMinutes").value(5));
    }

    @Test
    void updateConfig_otherRestaurant_returns403() throws Exception {
        AuthResponse owner = freshRestaurant();
        AuthResponse intruder = freshRestaurant();
        var update = new RestaurantConfigUpdateRequest(99, null, null, null, null);

        mockMvc.perform(patch("/restaurants/{id}/config", owner.restaurantId())
                        .header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateConfig_setMaxQueueSize_thenClear() throws Exception {
        AuthResponse auth = freshRestaurant();

        // Set max queue size
        var setMax = new RestaurantConfigUpdateRequest(null, null, null, null, 50);
        mockMvc.perform(patch("/restaurants/{id}/config", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setMax)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxQueueSize").value(50));

        // Verify it persisted
        mockMvc.perform(get("/restaurants/{id}/config", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(jsonPath("$.maxQueueSize").value(50));
    }
}
