package com.keyloop.scheduler.api.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record DealershipResourcesResponse(
        UUID dealershipId,
        List<ServiceTypeView> serviceTypes,
        List<BayView> bays,
        List<TechnicianView> technicians
) {
    public record ServiceTypeView(UUID id, String code, String name, int durationMinutes, String requiredSkill) {
    }

    public record BayView(UUID id, String name, Set<String> capabilities) {
    }

    public record TechnicianView(UUID id, String name, Set<String> skills) {
    }
}
