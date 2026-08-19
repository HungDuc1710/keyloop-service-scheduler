package com.keyloop.scheduler.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityEngineTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final Instant NOW = Instant.parse("2026-08-19T08:00:00Z");

    @Test
    void halfOpenAdjacentIntervalsDoNotOverlap() {
        Instant aStart = Instant.parse("2026-08-21T08:00:00Z");
        Instant aEnd = Instant.parse("2026-08-21T09:00:00Z");
        Instant bStart = Instant.parse("2026-08-21T09:00:00Z");
        Instant bEnd = Instant.parse("2026-08-21T10:00:00Z");
        assertFalse(AvailabilityEngine.overlaps(aStart, aEnd, bStart, bEnd));
    }

    @Test
    void oneMinuteOverlapIsConflict() {
        Instant aStart = Instant.parse("2026-08-21T08:00:00Z");
        Instant aEnd = Instant.parse("2026-08-21T09:00:00Z");
        Instant bStart = Instant.parse("2026-08-21T08:59:00Z");
        Instant bEnd = Instant.parse("2026-08-21T10:00:00Z");
        assertTrue(AvailabilityEngine.overlaps(aStart, aEnd, bStart, bEnd));
    }

    @Test
    void durationPastCloseIsRejected() {
        Instant start = Instant.parse("2026-08-21T14:30:00Z"); // 15:30 London BST
        Instant end = AvailabilityEngine.endAt(start, 180);
        Optional<ErrorCode> code = AvailabilityEngine.validateWindow(start, end, LONDON, NOW);
        assertEquals(Optional.of(ErrorCode.DURATION_PAST_CLOSE), code);
    }

    @Test
    void weekendIsOutsideHours() {
        Instant start = Instant.parse("2026-08-22T08:00:00Z"); // Saturday
        Instant end = AvailabilityEngine.endAt(start, 60);
        assertEquals(Optional.of(ErrorCode.OUTSIDE_HOURS), AvailabilityEngine.validateWindow(start, end, LONDON, NOW));
    }

    @Test
    void missingSkillMeansNoAssignment() {
        UUID bay = UUID.randomUUID();
        UUID busyTech = UUID.randomUUID();
        List<AvailabilityEngine.Resource> bays = List.of(new AvailabilityEngine.Resource(bay, "Bay 1"));
        List<AvailabilityEngine.Resource> techs = List.of(); // unqualified filtered out by caller
        Instant start = Instant.parse("2026-08-21T08:00:00Z");
        Instant end = AvailabilityEngine.endAt(start, 60);
        assertTrue(AvailabilityEngine.assign(bays, techs, List.of(), start, end, null).isEmpty());
        assertEquals(ErrorCode.NO_TECH, AvailabilityEngine.classifyShortage(bays, techs, List.of(), start, end));
    }

    @Test
    void occupiedBayAndTechAreNotAssigned() {
        UUID bay = UUID.randomUUID();
        UUID tech = UUID.randomUUID();
        Instant start = Instant.parse("2026-08-21T08:00:00Z");
        Instant end = AvailabilityEngine.endAt(start, 60);
        var busy = List.of(new AvailabilityEngine.BusyInterval(bay, tech, UUID.randomUUID(), start, end));
        var assignment = AvailabilityEngine.assign(
                List.of(new AvailabilityEngine.Resource(bay, "Bay 1")),
                List.of(new AvailabilityEngine.Resource(tech, "Pat")),
                busy, start, end, null);
        assertTrue(assignment.isEmpty());
    }

    @Test
    void slotStartsRespectDurationBeforeClose() {
        List<Instant> starts = AvailabilityEngine.slotStarts(LocalDate.of(2026, 8, 21), LONDON, 180);
        Instant last = starts.getLast();
        Instant lastEnd = AvailabilityEngine.endAt(last, 180);
        assertFalse(lastEnd.atZone(LONDON).toLocalTime().isAfter(AvailabilityEngine.CLOSE));
        assertFalse(starts.stream().anyMatch(s -> s.atZone(LONDON).toLocalTime().equals(java.time.LocalTime.of(15, 30))));
    }
}
