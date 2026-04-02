package com.queuetable.e2e;

import com.jayway.jsonpath.JsonPath;
import com.queuetable.auth.dto.AuthResponse;
import com.queuetable.queue.dto.JoinQueueRequest;
import com.queuetable.queue.dto.SeatQueueEntryRequest;
import com.queuetable.reservation.dto.CreateReservationRequest;
import com.queuetable.reservation.dto.SeatReservationRequest;
import com.queuetable.shared.AbstractIntegrationTest;
import com.queuetable.table.dto.CreateTableRequest;
import com.queuetable.table.dto.UpdateTableStatusRequest;
import com.queuetable.table.domain.TableStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FullFlowE2ETest extends AbstractIntegrationTest {

    private AuthResponse freshRestaurant() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return registerRestaurant("e2e-" + id, id + "@test.com");
    }

    private String bearer(AuthResponse auth) {
        return "Bearer " + auth.accessToken();
    }

    private String slugOf(AuthResponse auth) throws Exception {
        MvcResult result = mockMvc.perform(get("/restaurants/{id}", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
    }

    private String createTable(AuthResponse auth, String label, int capacity) throws Exception {
        MvcResult result = mockMvc.perform(post("/restaurants/{id}/tables", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTableRequest(label, capacity, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    // =========================================================================
    // E2E: Queue full lifecycle via QR
    // register → tables → QR join → notify → confirm → seat → table cleanup
    // =========================================================================

    @Test
    void queueFullLifecycle_qrFlow() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);
        String tableId = createTable(auth, "Mesa E2E-1", 4);

        // 1. Client checks queue status (empty)
        mockMvc.perform(get("/public/restaurants/{slug}/queue/status", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingCount").value(0))
                .andExpect(jsonPath("$.queueOpen").value(true));

        // 2. Client joins queue via QR
        MvcResult joinResult = mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinQueueRequest("E2E Customer", 2, "555-E2E"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1))
                .andReturn();

        String entryId = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.entryId");
        String accessToken = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.accessToken");

        // 3. Client tracks position
        mockMvc.perform(get("/public/queue/{entryId}", entryId)
                        .param("accessToken", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1));

        // 4. Staff views queue
        mockMvc.perform(get("/restaurants/{id}/queue", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // 5. Staff notifies client
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/notify", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"));

        // 6. Client confirms
        mockMvc.perform(post("/public/queue/{entryId}/confirm", entryId)
                        .param("accessToken", accessToken))
                .andExpect(status().isOk());

        // 7. Staff seats client
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/seat", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SeatQueueEntryRequest(UUID.fromString(tableId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEATED"));

        // 8. Table is OCCUPIED
        mockMvc.perform(get("/restaurants/{id}/tables", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(jsonPath("$[0].status").value("OCCUPIED"));

        // 9. Staff marks table as CLEANING
        mockMvc.perform(patch("/tables/{id}/status", tableId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTableStatusRequest(TableStatus.CLEANING))))
                .andExpect(status().isOk());

        // 10. Staff marks table as FREE
        mockMvc.perform(patch("/tables/{id}/status", tableId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTableStatusRequest(TableStatus.FREE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FREE"));

        // 11. Queue is now empty
        mockMvc.perform(get("/restaurants/{id}/queue", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // =========================================================================
    // E2E: Reservation full lifecycle
    // create → arrive → seat → complete → table cleaning → free
    // =========================================================================

    @Test
    void reservationFullLifecycle() throws Exception {
        AuthResponse auth = freshRestaurant();
        String tableId = createTable(auth, "Mesa Res-E2E", 4);

        // 1. Staff creates reservation
        MvcResult resResult = mockMvc.perform(post("/restaurants/{id}/reservations", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                "E2E Reserva", "555-RES", 3,
                                Instant.now().plus(1, ChronoUnit.HOURS), "Mesa ventana"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andReturn();

        String resId = JsonPath.read(resResult.getResponse().getContentAsString(), "$.id");

        // 2. Client arrives
        mockMvc.perform(post("/reservations/{id}/arrive", resId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"));

        // 3. Staff seats at table
        mockMvc.perform(post("/reservations/{id}/seat", resId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SeatReservationRequest(UUID.fromString(tableId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEATED"))
                .andExpect(jsonPath("$.tableId").value(tableId));

        // 4. Table is OCCUPIED
        mockMvc.perform(get("/restaurants/{id}/tables", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(jsonPath("$[0].status").value("OCCUPIED"));

        // 5. Visit complete → table goes to CLEANING
        mockMvc.perform(post("/reservations/{id}/complete", resId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 6. Table is now CLEANING
        mockMvc.perform(get("/restaurants/{id}/tables", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(jsonPath("$[0].status").value("CLEANING"));

        // 7. Staff marks table FREE
        mockMvc.perform(patch("/tables/{id}/status", tableId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTableStatusRequest(TableStatus.FREE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FREE"));
    }

    // =========================================================================
    // E2E: Walk-in full flow
    // staff adds walk-in → seat directly → table occupied
    // =========================================================================

    @Test
    void walkInFullFlow() throws Exception {
        AuthResponse auth = freshRestaurant();
        String tableId = createTable(auth, "Mesa Walk", 6);

        // 1. Staff adds walk-in
        MvcResult walkResult = mockMvc.perform(post("/restaurants/{id}/queue", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinQueueRequest("Walk-in Family", 4, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.walkIn").value(true))
                .andReturn();

        String entryId = JsonPath.read(walkResult.getResponse().getContentAsString(), "$.id");

        // 2. Staff seats directly (WAITING → SEATED)
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/seat", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SeatQueueEntryRequest(UUID.fromString(tableId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEATED"));

        // 3. Table is OCCUPIED
        mockMvc.perform(get("/restaurants/{id}/tables", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(jsonPath("$[0].status").value("OCCUPIED"));
    }

    // =========================================================================
    // E2E: Multiple groups competing for tables
    // =========================================================================

    @Test
    void multipleGroupsQueuePriority() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);
        String tableId = createTable(auth, "Mesa Unica", 4);

        // Three groups join
        MvcResult r1 = mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinQueueRequest("Primero", 2, null))))
                .andExpect(status().isCreated()).andReturn();
        MvcResult r2 = mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinQueueRequest("Segundo", 2, null))))
                .andExpect(status().isCreated()).andReturn();
        MvcResult r3 = mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinQueueRequest("Tercero", 2, null))))
                .andExpect(status().isCreated()).andReturn();

        String entryId1 = JsonPath.read(r1.getResponse().getContentAsString(), "$.entryId");
        String token3 = JsonPath.read(r3.getResponse().getContentAsString(), "$.accessToken");
        String entryId3 = JsonPath.read(r3.getResponse().getContentAsString(), "$.entryId");

        // Seat first group
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/seat", auth.restaurantId(), entryId1)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SeatQueueEntryRequest(UUID.fromString(tableId)))))
                .andExpect(status().isOk());

        // Third group checks position — should be 2 (Second=1, Third=2)
        mockMvc.perform(get("/public/queue/{entryId}", entryId3)
                        .param("accessToken", token3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(2));

        // No available tables (only one, and it's occupied)
        mockMvc.perform(get("/restaurants/{id}/tables/available", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .param("groupSize", "2"))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
