package com.keyloop.scheduler.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull UUID customerId,
        @NotNull UUID vehicleId,
        @NotNull UUID dealershipId,
        @NotNull UUID serviceTypeId,
        @NotNull OffsetDateTime startAt
) {
}
