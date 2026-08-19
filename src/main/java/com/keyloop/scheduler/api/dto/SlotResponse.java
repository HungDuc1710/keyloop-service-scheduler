package com.keyloop.scheduler.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SlotResponse(
        Instant startAt,
        Instant endAt,
        UUID exampleBayId,
        String exampleBayName,
        UUID exampleTechnicianId,
        String exampleTechnicianName
) {
}
