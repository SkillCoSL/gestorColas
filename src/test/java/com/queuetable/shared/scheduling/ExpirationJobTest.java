package com.queuetable.shared.scheduling;

import com.jayway.jsonpath.JsonPath;
import com.queuetable.auth.dto.AuthResponse;
import com.queuetable.config.domain.RestaurantConfigRepository;
import com.queuetable.queue.domain.QueueEntry;
import com.queuetable.queue.domain.QueueEntryRepository;
import com.queuetable.queue.domain.QueueEntryStatus;
import com.queuetable.queue.dto.JoinQueueRequest;
import com.queuetable.reservation.domain.Reservation;
import com.queuetable.reservation.domain.ReservationRepository;
import com.queuetable.reservation.domain.ReservationStatus;
import com.queuetable.reservation.dto.CreateReservationRequest;
import com.queuetable.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExpirationJobTest extends AbstractIntegrationTest {

    @Autowired
    private ExpirationJob expirationJob;

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RestaurantConfigRepository configRepository;

    private AuthResponse freshRestaurant() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return registerRestaurant("exp-" + id, id + "@test.com");
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

    @Test
    void expiresUnconfirmedQueueEntries() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        // Join queue
        var joinReq = new JoinQueueRequest("Expiring", 2, null);
        MvcResult joinResult = mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String entryId = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.entryId");

        // Notify
        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/notify", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk());

        // Simulate time passing: set notifiedAt to 10 minutes ago
        QueueEntry entry = queueEntryRepository.findById(UUID.fromString(entryId)).orElseThrow();
        entry.setNotifiedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        queueEntryRepository.save(entry);

        // Run expiration job
        expirationJob.processExpirations();

        // Verify expired
        QueueEntry expired = queueEntryRepository.findById(UUID.fromString(entryId)).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(QueueEntryStatus.EXPIRED);
    }

    @Test
    void expiresNoShowReservations() throws Exception {
        AuthResponse auth = freshRestaurant();

        // Create reservation in the past (30 minutes ago)
        var resReq = new CreateReservationRequest(
                "No Show", null, 2,
                Instant.now().minus(30, ChronoUnit.MINUTES), null);

        MvcResult result = mockMvc.perform(post("/restaurants/{id}/reservations", auth.restaurantId())
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String resId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        // Run expiration job
        expirationJob.processExpirations();

        // Verify no-show
        Reservation noShow = reservationRepository.findById(UUID.fromString(resId)).orElseThrow();
        assertThat(noShow.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
    }

    @Test
    void doesNotExpireRecentNotifications() throws Exception {
        AuthResponse auth = freshRestaurant();
        String slug = slugOf(auth);

        // Join and notify
        var joinReq = new JoinQueueRequest("Recent", 2, null);
        MvcResult joinResult = mockMvc.perform(post("/public/restaurants/{slug}/queue", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String entryId = JsonPath.read(joinResult.getResponse().getContentAsString(), "$.entryId");

        mockMvc.perform(post("/restaurants/{rid}/queue/{eid}/notify", auth.restaurantId(), entryId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk());

        // Run expiration job immediately (notifiedAt is just now)
        expirationJob.processExpirations();

        // Should still be NOTIFIED
        QueueEntry entry = queueEntryRepository.findById(UUID.fromString(entryId)).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.NOTIFIED);
    }
}
