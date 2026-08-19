package com.keyloop.scheduler.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID dealershipId,
        String dealershipName,
        UUID customerId,
        String customerName,
        UUID vehicleId,
        String vehicleRegistration,
        UUID serviceTypeId,
        String serviceTypeCode,
        UUID serviceBayId,
        String serviceBayName,
        UUID technicianId,
        String technicianName,
        Instant startAt,
        Instant endAt,
        String status
) {
}
