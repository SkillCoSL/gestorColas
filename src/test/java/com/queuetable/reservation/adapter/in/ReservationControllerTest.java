package com.queuetable.reservation.adapter.in;

import com.jayway.jsonpath.JsonPath;
import com.queuetable.auth.dto.AuthResponse;
import com.queuetable.reservation.dto.CreateReservationRequest;
import com.queuetable.reservation.dto.SeatReservationRequest;
import com.queuetable.reservation.dto.UpdateReservationRequest;
import com.queuetable.shared.AbstractIntegrationTest;
import com.queuetable.table.dto.CreateTableRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationControllerTest extends AbstractIntegrationTest {

    private AuthResponse freshRestaurant() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return registerRestaurant("res-" + id, id + "@test.com");
    }

    private String bearer(AuthResponse auth) {
        return "Bearer " + auth.accessToken();
    }

    private String createReservation(AuthResponse auth, String name, int partySize) throws Exception {
        var request = new CreateReservationRequest(
                name, null, partySize,
                Instant.now().plus(2, ChronoUnit.HOURS), null);

        MvcResult result = mockMvc.perform(post("/restaurants/{id}/reservations", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTable(AuthResponse auth, String label, int capacity) throws Exception {
        var request = new CreateTableRequest(label, capacity, null);
        MvcResult result = mockMvc.perform(post("/restaurants/{id}/tables", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @Test
    void createReservation_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        var request = new CreateReservationRequest(
                "John Doe", "555-1234", 4,
                Instant.now().plus(1, ChronoUnit.HOURS), "Window seat");

        mockMvc.perform(post("/restaurants/{id}/reservations", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("John Doe"))
                .andExpect(jsonPath("$.partySize").value(4))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.notes").value("Window seat"));
    }

    @Test
    void listReservations_empty() throws Exception {
        AuthResponse auth = freshRestaurant();

        mockMvc.perform(get("/restaurants/{id}/reservations", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listReservations_filterByStatus() throws Exception {
        AuthResponse auth = freshRestaurant();
        createReservation(auth, "Booked Person", 2);

        mockMvc.perform(get("/restaurants/{id}/reservations", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .param("status", "BOOKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/restaurants/{id}/reservations", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updateReservation_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String resId = createReservation(auth, "Original", 2);

        var update = new UpdateReservationRequest("Updated", null, 5, null, null);

        mockMvc.perform(patch("/reservations/{id}", resId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Updated"))
                .andExpect(jsonPath("$.partySize").value(5));
    }

    @Test
    void updateReservation_notBooked_returns400() throws Exception {
        AuthResponse auth = freshRestaurant();
        String resId = createReservation(auth, "Test", 2);

        // Arrive first
        mockMvc.perform(post("/reservations/{id}/arrive", resId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk());

        // Now try to update → should fail
        var update = new UpdateReservationRequest("New Name", null, null, null, null);
        mockMvc.perform(patch("/reservations/{id}", resId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // State transitions
    // -------------------------------------------------------------------------

    @Test
    void arrive_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String resId = createReservation(auth, "Arriving", 2);

        mockMvc.perform(post("/reservations/{id}/arrive", resId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"));
    }

    @Test
    void seat_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String resId = createReservation(auth, "Seating", 2);
        String tableId = createTable(auth, "Mesa R1", 4);

        // BOOKED → ARRIVED
        mockMvc.perform(post("/reservations/{id}/arrive", resId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk());

        // ARRIVED → SEATED
        var seatReq = new SeatReservationRequest(UUID.fromString(tableId));
        mockMvc.perform(post("/reservations/{id}/seat", resId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEATED"))
                .andExpect(jsonPath("$.tableId").value(tableId));
    }

    @Test
    void seat_tableNotFree_returns400() throws Exception {
        AuthResponse auth = freshRestaurant();
        String resId1 = createReservation(auth, "First", 2);
        String resId2 = createReservation(auth, "Second", 2);
        String tableId = createTable(auth, "Mesa R2", 4);

        // Seat first reservation
        mockMvc.perform(post("/reservations/{id}/arrive", resId1)
                .header("Authorization", bearer(auth)));
        var seatReq = new SeatReservationRequest(UUID.fromString(tableId));
        mockMvc.perform(post("/reservations/{id}/seat", resId1)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isOk());

        // Try to seat second at same table → 400
        mockMvc.perform(post("/reservations/{id}/arrive", resId2)
                .header("Authorization", bearer(auth)));
        mockMvc.perform(post("/reservations/{id}/seat", resId2)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void seat_tableTooSmall_returns400() throws Exception {
        AuthResponse auth = freshRestaurant();
        String resId = createReservation(auth, "Big Group", 6);
        String tableId = createTable(auth, "Mesa Small", 2);

        mockMvc.perform(post("/reservations/{id}/arrive", resId)
                .header("Authorization", bearer(auth)));

        var seatReq = new SeatReservationRequest(UUID.fromString(tableId));
        mockMvc.perform(post("/reservations/{id}/seat", resId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String resId = createReservation(auth, "Cancelling", 2);

        mockMvc.perform(post("/reservations/{id}/cancel", resId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void noShow_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String resId = createReservation(auth, "No Show", 2);

        mockMvc.perform(post("/reservations/{id}/no-show", resId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void invalidTransition_returns400() throws Exception {
        AuthResponse auth = freshRestaurant();
        String resId = createReservation(auth, "Invalid", 2);

        // BOOKED → SEATED directly (invalid, must go through ARRIVED)
        String tableId = createTable(auth, "Mesa X", 4);
        var seatReq = new SeatReservationRequest(UUID.fromString(tableId));
        mockMvc.perform(post("/reservations/{id}/seat", resId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Security
    // -------------------------------------------------------------------------

    @Test
    void accessOtherRestaurantReservation_returns403() throws Exception {
        AuthResponse owner = freshRestaurant();
        AuthResponse intruder = freshRestaurant();

        String resId = createReservation(owner, "Private", 2);

        // Intruder tries to list owner's reservations
        mockMvc.perform(get("/restaurants/{id}/reservations", owner.restaurantId())
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());

        // Intruder tries to cancel owner's reservation
        mockMvc.perform(post("/reservations/{id}/cancel", resId)
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }
}
