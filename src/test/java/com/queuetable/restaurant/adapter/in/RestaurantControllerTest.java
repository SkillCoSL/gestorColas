package com.queuetable.restaurant.adapter.in;

import com.queuetable.auth.dto.AuthResponse;
import com.queuetable.restaurant.dto.RestaurantUpdateRequest;
import com.queuetable.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RestaurantControllerTest extends AbstractIntegrationTest {

    private AuthResponse freshRestaurant() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return registerRestaurant("rest-" + id, id + "@test.com");
    }

    private String bearer(AuthResponse auth) {
        return "Bearer " + auth.accessToken();
    }

    // -------------------------------------------------------------------------
    // GET /restaurants/{id}
    // -------------------------------------------------------------------------

    @Test
    void getById_success() throws Exception {
        AuthResponse auth = freshRestaurant();

        mockMvc.perform(get("/restaurants/{id}", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(auth.restaurantId().toString()))
                .andExpect(jsonPath("$.name").value("Test Restaurant"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getById_otherRestaurant_returns403() throws Exception {
        AuthResponse owner = freshRestaurant();
        AuthResponse other = freshRestaurant();

        mockMvc.perform(get("/restaurants/{id}", owner.restaurantId())
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_noAuth_returns401() throws Exception {
        AuthResponse auth = freshRestaurant();

        mockMvc.perform(get("/restaurants/{id}", auth.restaurantId()))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // PATCH /restaurants/{id}
    // -------------------------------------------------------------------------

    @Test
    void update_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        var update = new RestaurantUpdateRequest(
                "Updated Name", "New Address 123", "+34 999 000", "Great food", null);

        mockMvc.perform(patch("/restaurants/{id}", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.address").value("New Address 123"))
                .andExpect(jsonPath("$.phone").value("+34 999 000"))
                .andExpect(jsonPath("$.description").value("Great food"));
    }

    @Test
    void update_partialFields() throws Exception {
        AuthResponse auth = freshRestaurant();
        var update = new RestaurantUpdateRequest(null, null, null, "Only description", null);

        mockMvc.perform(patch("/restaurants/{id}", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Only description"))
                .andExpect(jsonPath("$.name").value("Test Restaurant"));
    }

    @Test
    void update_otherRestaurant_returns403() throws Exception {
        AuthResponse owner = freshRestaurant();
        AuthResponse intruder = freshRestaurant();
        var update = new RestaurantUpdateRequest("Hacked", null, null, null, null);

        mockMvc.perform(patch("/restaurants/{id}", owner.restaurantId())
                        .header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /restaurants/{id}/qr
    // -------------------------------------------------------------------------

    @Test
    void getQrCode_returnsPng() throws Exception {
        AuthResponse auth = freshRestaurant();

        mockMvc.perform(get("/restaurants/{id}/qr", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void getQrCode_otherRestaurant_returns403() throws Exception {
        AuthResponse owner = freshRestaurant();
        AuthResponse other = freshRestaurant();

        mockMvc.perform(get("/restaurants/{id}/qr", owner.restaurantId())
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
    }
}
