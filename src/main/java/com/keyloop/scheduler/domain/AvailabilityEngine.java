package com.keyloop.scheduler.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure scheduling rules: half-open intervals, dealer hours, 30-minute grid.
 * Used by both availability search and booking confirmation.
 */
public final class AvailabilityEngine {

    public static final LocalTime OPEN = LocalTime.of(8, 0);
    public static final LocalTime CLOSE = LocalTime.of(17, 0);
    public static final int SLOT_MINUTES = 30;

    public record BusyInterval(UUID bayId, UUID technicianId, UUID vehicleId, Instant start, Instant end) {
    }

    public record ResourceAssignment(UUID bayId, String bayName, UUID technicianId, String technicianName) {
    }

    public record Resource(UUID id, String name) {
    }

    private AvailabilityEngine() {
    }

    public static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    public static Instant endAt(Instant start, int durationMinutes) {
        return start.plus(Duration.ofMinutes(durationMinutes));
    }

    public static Optional<ErrorCode> validateWindow(Instant start, Instant end, ZoneId zone, Instant now) {
        if (start.isBefore(now)) {
            return Optional.of(ErrorCode.PAST_START);
        }

        ZonedDateTime localStart = start.atZone(zone);
        ZonedDateTime localEnd = end.atZone(zone);

        if (localStart.getSecond() != 0 || localStart.getNano() != 0
                || localStart.getMinute() % SLOT_MINUTES != 0) {
            return Optional.of(ErrorCode.NOT_ON_GRID);
        }

        DayOfWeek day = localStart.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return Optional.of(ErrorCode.OUTSIDE_HOURS);
        }

        LocalTime tStart = localStart.toLocalTime();
        LocalTime tEnd = localEnd.toLocalTime();

        if (tStart.isBefore(OPEN) || !tStart.isBefore(CLOSE)) {
            return Optional.of(ErrorCode.OUTSIDE_HOURS);
        }
        if (!localEnd.toLocalDate().equals(localStart.toLocalDate()) || tEnd.isAfter(CLOSE)) {
            return Optional.of(ErrorCode.DURATION_PAST_CLOSE);
        }
        return Optional.empty();
    }

    public static List<Instant> slotStarts(java.time.LocalDate date, ZoneId zone, int durationMinutes) {
        List<Instant> starts = new ArrayList<>();
        ZonedDateTime cursor = date.atTime(OPEN).atZone(zone);
        ZonedDateTime close = date.atTime(CLOSE).atZone(zone);
        Duration duration = Duration.ofMinutes(durationMinutes);
        while (!cursor.plus(duration).isAfter(close)) {
            starts.add(cursor.toInstant());
            cursor = cursor.plusMinutes(SLOT_MINUTES);
        }
        return starts;
    }

    public static Optional<ResourceAssignment> assign(
            List<Resource> bays,
            List<Resource> technicians,
            List<BusyInterval> busy,
            Instant start,
            Instant end,
            UUID vehicleId
    ) {
        if (vehicleId != null && busy.stream().anyMatch(b ->
                vehicleId.equals(b.vehicleId()) && overlaps(b.start(), b.end(), start, end))) {
            return Optional.empty();
        }
        for (Resource bay : bays) {
            boolean bayFree = busy.stream().noneMatch(b ->
                    bay.id().equals(b.bayId()) && overlaps(b.start(), b.end(), start, end));
            if (!bayFree) {
                continue;
            }
            for (Resource tech : technicians) {
                boolean techFree = busy.stream().noneMatch(b ->
                        tech.id().equals(b.technicianId()) && overlaps(b.start(), b.end(), start, end));
                if (techFree) {
                    return Optional.of(new ResourceAssignment(bay.id(), bay.name(), tech.id(), tech.name()));
                }
            }
        }
        return Optional.empty();
    }

    public static ErrorCode classifyShortage(List<Resource> bays, List<Resource> technicians,
                                             List<BusyInterval> busy, Instant start, Instant end) {
        boolean anyBay = bays.stream().anyMatch(bay -> busy.stream().noneMatch(b ->
                bay.id().equals(b.bayId()) && overlaps(b.start(), b.end(), start, end)));
        boolean anyTech = technicians.stream().anyMatch(tech -> busy.stream().noneMatch(b ->
                tech.id().equals(b.technicianId()) && overlaps(b.start(), b.end(), start, end)));
        if (!anyBay) {
            return ErrorCode.NO_BAY;
        }
        if (!anyTech) {
            return ErrorCode.NO_TECH;
        }
        return ErrorCode.SLOT_UNAVAILABLE;
    }
}
