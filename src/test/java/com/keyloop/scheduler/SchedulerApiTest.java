package com.keyloop.scheduler;

import com.keyloop.scheduler.api.dto.AppointmentResponse;
import com.keyloop.scheduler.api.dto.CreateAppointmentRequest;
import com.keyloop.scheduler.api.dto.ErrorResponse;
import com.keyloop.scheduler.seed.SeedIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SchedulerApiTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void healthAndReadyAreUp() {
        assertEquals(HttpStatus.OK, http.getForEntity("/health", Map.class).getStatusCode());
        assertEquals(HttpStatus.OK, http.getForEntity("/ready", Map.class).getStatusCode());
        String metrics = http.getForObject("/metrics", String.class);
        assertNotNull(metrics);
        assertTrue(metrics.contains("http_requests") || metrics.contains("jvm_"));
    }

    @Test
    void booksDesiredTimeAndReturnsFourAssociations() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-21T08:00:00Z");
        ResponseEntity<AppointmentResponse> created = book(start, SeedIds.SERVICE_MOT, SeedIds.VEHICLE, SeedIds.CUSTOMER, UUID.randomUUID().toString());
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        AppointmentResponse body = created.getBody();
        assertNotNull(body);
        assertEquals(SeedIds.CUSTOMER, body.customerId());
        assertEquals(SeedIds.VEHICLE, body.vehicleId());
        assertNotNull(body.technicianId());
        assertNotNull(body.serviceBayId());
        assertEquals("CONFIRMED", body.status());
        assertEquals(start.toInstant().plusSeconds(3600), body.endAt());
    }

    @Test
    void overlappingMajorServiceConflictsBecauseOnlyOneQualifiedTech() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-21T10:00:00Z");
        ResponseEntity<AppointmentResponse> first = book(start, SeedIds.SERVICE_MAJOR, SeedIds.VEHICLE, SeedIds.CUSTOMER, "major-1");
        assertEquals(HttpStatus.CREATED, first.getStatusCode());

        ResponseEntity<ErrorResponse> second = bookExpectingError(
                start, SeedIds.SERVICE_MAJOR, SeedIds.OTHER_VEHICLE, SeedIds.OTHER_CUSTOMER, "major-2");
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
        assertNotNull(second.getBody());
        assertEquals("NO_TECH", second.getBody().code());
        assertNotNull(second.getHeaders().getFirst("X-Request-ID"));
    }

    @Test
    void vehicleOwnershipIsEnforced() {
        ResponseEntity<ErrorResponse> response = bookExpectingError(
                OffsetDateTime.parse("2026-08-21T11:00:00Z"),
                SeedIds.SERVICE_MOT,
                SeedIds.OTHER_VEHICLE,
                SeedIds.CUSTOMER,
                UUID.randomUUID().toString());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("VEHICLE_NOT_OWNED", response.getBody().code());
    }

    @Test
    void vehicleDoubleBookIsRejected() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-21T12:00:00Z");
        assertEquals(HttpStatus.CREATED, book(start, SeedIds.SERVICE_MOT, SeedIds.VEHICLE, SeedIds.CUSTOMER, "veh-1").getStatusCode());
        ResponseEntity<ErrorResponse> second = bookExpectingError(
                start, SeedIds.SERVICE_MOT, SeedIds.VEHICLE, SeedIds.CUSTOMER, "veh-2");
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
        assertEquals("VEHICLE_OVERLAP", second.getBody().code());
    }

    @Test
    void cancelFreesTheSlot() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-21T13:00:00Z");
        AppointmentResponse created = book(start, SeedIds.SERVICE_MOT, SeedIds.VEHICLE, SeedIds.CUSTOMER, "cancel-1").getBody();
        assertNotNull(created);
        ResponseEntity<AppointmentResponse> cancelled = http.postForEntity(
                "/appointments/" + created.id() + "/cancel", null, AppointmentResponse.class);
        assertEquals(HttpStatus.OK, cancelled.getStatusCode());
        assertEquals("CANCELLED", cancelled.getBody().status());

        ResponseEntity<AppointmentResponse> again = book(start, SeedIds.SERVICE_MOT, SeedIds.VEHICLE, SeedIds.CUSTOMER, "cancel-2");
        assertEquals(HttpStatus.CREATED, again.getStatusCode());
    }

    @Test
    void idempotencyReplaysTheSameAppointment() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-20T08:00:00Z"); // Thursday
        String key = "idem-replay";
        ResponseEntity<AppointmentResponse> first = book(start, SeedIds.SERVICE_MOT, SeedIds.VEHICLE, SeedIds.CUSTOMER, key);
        ResponseEntity<AppointmentResponse> second = book(start, SeedIds.SERVICE_MOT, SeedIds.VEHICLE, SeedIds.CUSTOMER, key);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals(first.getBody().id(), second.getBody().id());
    }

    @Test
    void naiveDatetimeIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "naive");
        String body = """
                {"customerId":"%s","vehicleId":"%s","dealershipId":"%s","serviceTypeId":"%s","startAt":"2026-08-21T09:00:00"}
                """.formatted(SeedIds.CUSTOMER, SeedIds.VEHICLE, SeedIds.DEALERSHIP, SeedIds.SERVICE_MOT);
        ResponseEntity<ErrorResponse> response = http.exchange(
                "/appointments", HttpMethod.POST, new HttpEntity<>(body, headers), ErrorResponse.class);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
    }

    @Test
    void concurrentMajorBookingsYieldOneCreatedAndOneConflict() throws Exception {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-21T07:00:00Z"); // 08:00 London
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<ResponseEntity<String>> a = pool.submit(() -> fire(start, "conc-a", ready, go));
            Future<ResponseEntity<String>> b = pool.submit(() -> fire(start, "conc-b", ready, go));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            int codeA = a.get(10, TimeUnit.SECONDS).getStatusCode().value();
            int codeB = b.get(10, TimeUnit.SECONDS).getStatusCode().value();
            java.util.List<Integer> codes = java.util.List.of(codeA, codeB);
            assertTrue(codes.contains(201) && codes.contains(409), "statuses=" + codes
                    + " bodies=" + a.get().getBody() + " | " + b.get().getBody());
        } finally {
            pool.shutdownNow();
        }
    }

    private ResponseEntity<String> fire(OffsetDateTime start, String key, CountDownLatch ready, CountDownLatch go)
            throws InterruptedException {
        ready.countDown();
        go.await(5, TimeUnit.SECONDS);
        return http.exchange("/appointments", HttpMethod.POST, entity(start, SeedIds.SERVICE_MAJOR,
                SeedIds.VEHICLE.equals(SeedIds.VEHICLE) && key.endsWith("a") ? SeedIds.VEHICLE : SeedIds.OTHER_VEHICLE,
                key.endsWith("a") ? SeedIds.CUSTOMER : SeedIds.OTHER_CUSTOMER, key), String.class);
    }

    private ResponseEntity<AppointmentResponse> book(
            OffsetDateTime start, UUID serviceType, UUID vehicle, UUID customer, String key) {
        return http.exchange("/appointments", HttpMethod.POST,
                entity(start, serviceType, vehicle, customer, key), AppointmentResponse.class);
    }

    private ResponseEntity<ErrorResponse> bookExpectingError(
            OffsetDateTime start, UUID serviceType, UUID vehicle, UUID customer, String key) {
        return http.exchange("/appointments", HttpMethod.POST,
                entity(start, serviceType, vehicle, customer, key), ErrorResponse.class);
    }

    private HttpEntity<CreateAppointmentRequest> entity(
            OffsetDateTime start, UUID serviceType, UUID vehicle, UUID customer, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        CreateAppointmentRequest body = new CreateAppointmentRequest(
                customer, vehicle, SeedIds.DEALERSHIP, serviceType, start);
        return new HttpEntity<>(body, headers);
    }
}
