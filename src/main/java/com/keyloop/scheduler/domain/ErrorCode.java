package com.keyloop.scheduler.domain;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND),
    VEHICLE_NOT_OWNED(HttpStatus.UNPROCESSABLE_ENTITY),
    SERVICE_NOT_OFFERED(HttpStatus.UNPROCESSABLE_ENTITY),
    OUTSIDE_HOURS(HttpStatus.CONFLICT),
    DURATION_PAST_CLOSE(HttpStatus.CONFLICT),
    PAST_START(HttpStatus.CONFLICT),
    NOT_ON_GRID(HttpStatus.UNPROCESSABLE_ENTITY),
    SLOT_UNAVAILABLE(HttpStatus.CONFLICT),
    NO_BAY(HttpStatus.CONFLICT),
    NO_TECH(HttpStatus.CONFLICT),
    VEHICLE_OVERLAP(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
