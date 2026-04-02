package com.queuetable.queue.adapter.in;

import com.jayway.jsonpath.JsonPath;
import com.queuetable.auth.dto.AuthResponse;
import com.queuetable.shared.AbstractIntegrationTest;
import com.queuetable.queue.dto.JoinQueueRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicQueueControllerTest extends AbstractIntegrationTest {

    private AuthResponse freshRestaurant() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return registerRestaurant("queue-" + id, id + "@test.com");
    }

    private String slugOf(AuthResponse auth) throws Exception {
        MvcResult result = mockMvc.perform(get("/restaurants/{id}", auth.restaurantId())
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
    }

    private MvcResult joinQueue(String slug, String name, int partySize) throws Exception {
        var request = new JoinQueueRequest(name, partySize, null);
        return mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    // -------------------------------------------------------------------------
    // GET /public/restaurants/{slug}
    // -------------------------------------------------------------------------

    @Test
    void getRestaurantInfo_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        mockMvc.perform(get("/public/restaurants/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Restaurant"))
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getRestaurantInfo_notFound_returns404() throws Exception {
        mockMvc.perform(get("/public/restaurants/{slug}", "nonexistent-slug"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // GET /public/restaurants/{slug}/queue/status
    // -------------------------------------------------------------------------

    @Test
    void getQueueStatus_empty() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        mockMvc.perform(get("/public/restaurants/{slug}/queue/status", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingCount").value(0))
                .andExpect(jsonPath("$.estimatedWaitMinutes").value(0))
                .andExpect(jsonPath("$.queueOpen").value(true));
    }

    @Test
    void getQueueStatus_afterJoining() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        joinQueue(slug, "Alice", 2);
        joinQueue(slug, "Bob", 4);

        mockMvc.perform(get("/public/restaurants/{slug}/queue/status", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingCount").value(2))
                .andExpect(jsonPath("$.queueOpen").value(true));
    }

    // -------------------------------------------------------------------------
    // POST /public/restaurants/{slug}/queue
    // -------------------------------------------------------------------------

    @Test
    void joinQueue_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        var request = new JoinQueueRequest("Carlos", 3, "555-1234");

        mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryId").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.estimatedWaitMinutes").value(0));
    }

    @Test
    void joinQueue_secondPosition() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        joinQueue(slug, "First", 2);

        mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinQueueRequest("Second", 2, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(2));
    }

    @Test
    void joinQueue_invalidPartySize_returns400() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        var request = new JoinQueueRequest("Test", 0, null);

        mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void joinQueue_blankName_returns400() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        var request = new JoinQueueRequest("", 2, null);

        mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void joinQueue_nonexistentRestaurant_returns404() throws Exception {
        var request = new JoinQueueRequest("Test", 2, null);

        mockMvc.perform(post("/public/restaurants/{slug}/queue", "no-existe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // GET /public/queue/{entryId}?accessToken=...
    // -------------------------------------------------------------------------

    @Test
    void getEntryStatus_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        MvcResult joinResult = joinQueue(slug, "Diana", 2);
        String body = joinResult.getResponse().getContentAsString();
        String entryId = JsonPath.read(body, "$.entryId");
        String accessToken = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/public/queue/{entryId}", entryId)
                        .param("accessToken", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Diana"))
                .andExpect(jsonPath("$.partySize").value(2))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1));
    }

    @Test
    void getEntryStatus_wrongToken_returns404() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        MvcResult joinResult = joinQueue(slug, "Eve", 2);
        String entryId = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.entryId");

        mockMvc.perform(get("/public/queue/{entryId}", entryId)
                        .param("accessToken", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // DELETE /public/queue/{entryId}?accessToken=...
    // -------------------------------------------------------------------------

    @Test
    void cancelEntry_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        MvcResult joinResult = joinQueue(slug, "Frank", 3);
        String body = joinResult.getResponse().getContentAsString();
        String entryId = JsonPath.read(body, "$.entryId");
        String accessToken = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(delete("/public/queue/{entryId}", entryId)
                        .param("accessToken", accessToken))
                .andExpect(status().isNoContent());

        // Verify cancelled
        mockMvc.perform(get("/public/queue/{entryId}", entryId)
                        .param("accessToken", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelEntry_wrongToken_returns404() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        MvcResult joinResult = joinQueue(slug, "Grace", 2);
        String entryId = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.entryId");

        mockMvc.perform(delete("/public/queue/{entryId}", entryId)
                        .param("accessToken", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelEntry_alreadyCancelled_returns400() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        MvcResult joinResult = joinQueue(slug, "Hank", 1);
        String body = joinResult.getResponse().getContentAsString();
        String entryId = JsonPath.read(body, "$.entryId");
        String accessToken = JsonPath.read(body, "$.accessToken");

        // Cancel first time
        mockMvc.perform(delete("/public/queue/{entryId}", entryId)
                        .param("accessToken", accessToken))
                .andExpect(status().isNoContent());

        // Cancel second time → 400
        mockMvc.perform(delete("/public/queue/{entryId}", entryId)
                        .param("accessToken", accessToken))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Position recalculation after cancel
    // -------------------------------------------------------------------------

    @Test
    void cancelEntry_recalculatesPositions() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        // Three people join
        MvcResult r1 = joinQueue(slug, "P1", 2);
        MvcResult r2 = joinQueue(slug, "P2", 2);
        MvcResult r3 = joinQueue(slug, "P3", 2);

        String entryId1 = JsonPath.read(r1.getResponse().getContentAsString(), "$.entryId");
        String token1 = JsonPath.read(r1.getResponse().getContentAsString(), "$.accessToken");
        String entryId3 = JsonPath.read(r3.getResponse().getContentAsString(), "$.entryId");
        String token3 = JsonPath.read(r3.getResponse().getContentAsString(), "$.accessToken");

        // Cancel P1
        mockMvc.perform(delete("/public/queue/{entryId}", entryId1)
                        .param("accessToken", token1))
                .andExpect(status().isNoContent());

        // P3 should now be at position 2
        mockMvc.perform(get("/public/queue/{entryId}", entryId3)
                        .param("accessToken", token3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(2));
    }

    // -------------------------------------------------------------------------
    // POST /public/queue/{entryId}/confirm
    // -------------------------------------------------------------------------

    @Test
    void confirmEntry_afterNotify_success() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        MvcResult joinResult = joinQueue(slug, "Confirmer", 2);
        String body = joinResult.getResponse().getContentAsString();
        String entryId = JsonPath.read(body, "$.entryId");
        String accessToken = JsonPath.read(body, "$.accessToken");

        // Staff notifies
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/notify", auth.restaurantId(), entryId)
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"));

        // Client confirms
        mockMvc.perform(post("/public/queue/{entryId}/confirm", entryId)
                        .param("accessToken", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"));
    }

    @Test
    void confirmEntry_notNotified_returns400() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        MvcResult joinResult = joinQueue(slug, "TooEarly", 2);
        String body = joinResult.getResponse().getContentAsString();
        String entryId = JsonPath.read(body, "$.entryId");
        String accessToken = JsonPath.read(body, "$.accessToken");

        // Client tries to confirm while still WAITING
        mockMvc.perform(post("/public/queue/{entryId}/confirm", entryId)
                        .param("accessToken", accessToken))
                .andExpect(status().isBadRequest());
    }
}
