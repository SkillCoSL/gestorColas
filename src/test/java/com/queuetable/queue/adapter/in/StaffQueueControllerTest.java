package com.queuetable.queue.adapter.in;

import com.jayway.jsonpath.JsonPath;
import com.queuetable.auth.dto.AuthResponse;
import com.queuetable.queue.dto.JoinQueueRequest;
import com.queuetable.queue.dto.SeatQueueEntryRequest;
import com.queuetable.shared.AbstractIntegrationTest;
import com.queuetable.table.dto.CreateTableRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffQueueControllerTest extends AbstractIntegrationTest {

    private AuthResponse freshRestaurant() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return registerRestaurant("staff-q-" + id, id + "@test.com");
    }

    private String bearer(AuthResponse auth) {
        return "Bearer " + auth.accessToken();
    }

    private String slugOf(AuthResponse auth) throws Exception {
        MvcResult result = mockMvc.perform(get("/restaurants/{id}", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
    }

    private MvcResult joinQueue(String slug, String name, int partySize) throws Exception {
        var request = new JoinQueueRequest(name, partySize, null);
        return mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    // -------------------------------------------------------------------------
    // GET /restaurants/{id}/queue
    // -------------------------------------------------------------------------

    @Test
    void listQueue_empty() throws Exception {
        AuthResponse auth = freshRestaurant();

        mockMvc.perform(get("/restaurants/{id}/queue", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listQueue_withEntries() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        joinQueue(slug, "Alice", 2);
        joinQueue(slug, "Bob", 4);

        mockMvc.perform(get("/restaurants/{id}/queue", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].customerName").value("Alice"))
                .andExpect(jsonPath("$[1].customerName").value("Bob"));
    }

    @Test
    void listQueue_filterByStatus() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        joinQueue(slug, "Active", 2);

        // All WAITING
        mockMvc.perform(get("/restaurants/{id}/queue", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .param("status", "WAITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // No CANCELLED
        mockMvc.perform(get("/restaurants/{id}/queue", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listQueue_otherRestaurant_returns403() throws Exception {
        AuthResponse owner = freshRestaurant();
        AuthResponse intruder = freshRestaurant();

        mockMvc.perform(get("/restaurants/{id}/queue", owner.restaurantId())
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // POST /restaurants/{id}/queue/{entryId}/cancel
    // -------------------------------------------------------------------------

    @Test
    void staffCancelEntry_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        MvcResult joinResult = joinQueue(slug, "Victim", 3);
        String entryId = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.entryId");

        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/cancel", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isNoContent());

        // Verify via staff list with status filter
        mockMvc.perform(get("/restaurants/{id}/queue", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    void staffCancelEntry_otherRestaurant_returns403() throws Exception {
        AuthResponse owner = freshRestaurant();
        AuthResponse intruder = freshRestaurant();
        String slug = slugOf(owner);

        MvcResult joinResult = joinQueue(slug, "Entry", 2);
        String entryId = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.entryId");

        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/cancel", owner.restaurantId(), entryId)
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCancelEntry_notFound_returns404() throws Exception {
        AuthResponse auth = freshRestaurant();

        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/cancel",
                        auth.restaurantId(), UUID.randomUUID())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // POST /restaurants/{id}/queue (walk-in)
    // -------------------------------------------------------------------------

    @Test
    void addWalkIn_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        var request = new JoinQueueRequest("Walk In Person", 3, "555-0000");

        mockMvc.perform(post("/restaurants/{id}/queue", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Walk In Person"))
                .andExpect(jsonPath("$.partySize").value(3))
                .andExpect(jsonPath("$.walkIn").value(true))
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void addWalkIn_otherRestaurant_returns403() throws Exception {
        AuthResponse owner = freshRestaurant();
        AuthResponse intruder = freshRestaurant();
        var request = new JoinQueueRequest("Sneaky", 2, null);

        mockMvc.perform(post("/restaurants/{id}/queue", owner.restaurantId())
                        .header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // POST /restaurants/{id}/queue/{entryId}/seat
    // -------------------------------------------------------------------------

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

    @Test
    void seatEntry_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);
        String tableId = createTable(auth, "Mesa S1", 4);

        MvcResult joinResult = joinQueue(slug, "Seatee", 2);
        String entryId = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.entryId");

        var seatReq = new SeatQueueEntryRequest(UUID.fromString(tableId));
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/seat", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEATED"));

        // Verify table is now OCCUPIED
        mockMvc.perform(get("/restaurants/{id}/tables", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OCCUPIED"));
    }

    @Test
    void seatEntry_tableTooSmall_returns400() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);
        String tableId = createTable(auth, "Mesa Tiny", 2);

        MvcResult joinResult = joinQueue(slug, "Big Group", 6);
        String entryId = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.entryId");

        var seatReq = new SeatQueueEntryRequest(UUID.fromString(tableId));
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/seat", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void seatEntry_tableNotFree_returns400() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);
        String tableId = createTable(auth, "Mesa Busy", 4);

        // Seat first person
        MvcResult r1 = joinQueue(slug, "First", 2);
        String entryId1 = JsonPath.read(r1.getResponse().getContentAsString(), "$.entryId");
        var seatReq = new SeatQueueEntryRequest(UUID.fromString(tableId));
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/seat", auth.restaurantId(), entryId1)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isOk());

        // Try to seat second at same table
        MvcResult r2 = joinQueue(slug, "Second", 2);
        String entryId2 = JsonPath.read(r2.getResponse().getContentAsString(), "$.entryId");
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/seat", auth.restaurantId(), entryId2)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /restaurants/{id}/tables/available
    // -------------------------------------------------------------------------

    @Test
    void getAvailableTables_filtersCorrectly() throws Exception {
        AuthResponse auth = freshRestaurant();
        createTable(auth, "Small", 2);
        createTable(auth, "Big", 6);

        // For group of 4, only Big should be available
        mockMvc.perform(get("/restaurants/{id}/tables/available", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .param("groupSize", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].label").value("Big"));

        // For group of 2, both available
        mockMvc.perform(get("/restaurants/{id}/tables/available", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .param("groupSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAvailableTables_excludesOccupied() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);
        String tableId = createTable(auth, "Occupied", 4);
        createTable(auth, "Free", 4);

        // Seat someone at first table
        MvcResult r = joinQueue(slug, "Seated", 2);
        String entryId = JsonPath.read(r.getResponse().getContentAsString(), "$.entryId");
        var seatReq = new SeatQueueEntryRequest(UUID.fromString(tableId));
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/seat", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isOk());

        // Only Free table should be available
        mockMvc.perform(get("/restaurants/{id}/tables/available", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .param("groupSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].label").value("Free"));
    }

    // -------------------------------------------------------------------------
    // POST /restaurants/{id}/queue/{entryId}/notify
    // -------------------------------------------------------------------------

    @Test
    void notifyEntry_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        MvcResult joinResult = joinQueue(slug, "Notifee", 2);
        String entryId = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.entryId");

        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/notify", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"))
                .andExpect(jsonPath("$.notifiedAt").isNotEmpty());
    }

    @Test
    void notifyEntry_alreadyNotified_returns400() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        MvcResult joinResult = joinQueue(slug, "DoubleNotify", 2);
        String entryId = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.entryId");

        // First notify
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/notify", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk());

        // Second notify → 400
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/notify", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /restaurants/{id}/queue/{entryId}/skip
    // -------------------------------------------------------------------------

    @Test
    void skipEntry_movesToEnd() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        MvcResult r1 = joinQueue(slug, "First", 2);
        joinQueue(slug, "Second", 2);

        String entryId1 = JsonPath.read(r1.getResponse().getContentAsString(), "$.entryId");

        // Skip first entry
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/skip", auth.restaurantId(), entryId1)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"));

        // Now listing: Second should be first, First should be last
        mockMvc.perform(get("/restaurants/{id}/queue", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerName").value("Second"))
                .andExpect(jsonPath("$[1].customerName").value("First"));
    }

    // -------------------------------------------------------------------------
    // Full flow: notify → confirm → seat
    // -------------------------------------------------------------------------

    @Test
    void fullFlow_notifyConfirmSeat() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);
        String tableId = createTable(auth, "Mesa Flow", 4);

        MvcResult joinResult = joinQueue(slug, "FullFlow", 2);
        String body = joinResult.getResponse().getContentAsString();
        String entryId = JsonPath.read(body, "$.entryId");
        String accessToken = JsonPath.read(body, "$.accessToken");

        // 1. Staff notifies
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/notify", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"));

        // 2. Client confirms
        mockMvc.perform(post("/public/queue/{entryId}/confirm", entryId)
                        .param("accessToken", accessToken))
                .andExpect(status().isOk());

        // 3. Staff seats
        var seatReq = new SeatQueueEntryRequest(UUID.fromString(tableId));
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/seat", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEATED"));

        // Verify table is OCCUPIED
        mockMvc.perform(get("/restaurants/{id}/tables", auth.restaurantId())
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OCCUPIED"));
    }
}
