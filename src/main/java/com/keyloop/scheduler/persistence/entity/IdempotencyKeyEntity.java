package com.keyloop.scheduler.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "idempotency_key")
    private String key;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Lob
    @Column(name = "response_json", nullable = false)
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {
    }

    public IdempotencyKeyEntity(
            String key,
            String requestHash,
            UUID appointmentId,
            int statusCode,
            String responseJson,
            Instant createdAt
    ) {
        this.key = key;
        this.requestHash = requestHash;
        this.appointmentId = appointmentId;
        this.statusCode = statusCode;
        this.responseJson = responseJson;
        this.createdAt = createdAt;
    }

    public String getKey() {
        return key;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseJson() {
        return responseJson;
    }
}
