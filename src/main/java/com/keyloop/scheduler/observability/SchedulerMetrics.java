package com.keyloop.scheduler.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class SchedulerMetrics {

    private static final Logger log = LoggerFactory.getLogger(SchedulerMetrics.class);

    private final MeterRegistry meterRegistry;

    public SchedulerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAvailability(UUID dealershipId, int resultCount, long durationMs) {
        Timer.builder("availability_duration").register(meterRegistry).record(durationMs, TimeUnit.MILLISECONDS);
        log.info("event=availability.checked dealershipId={} resultCount={} duration_ms={}",
                dealershipId, resultCount, durationMs);
    }

    public void recordOverlap(String resourceType, boolean hit, long durationMs) {
        Timer.builder("overlap_check_duration")
                .tags("resource_type", resourceType)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
        log.info("event=overlap.checked resourceType={} hit={} duration_ms={}", resourceType, hit, durationMs);
    }

    public void bookingAttempted(UUID dealershipId) {
        log.info("event=booking.attempted dealershipId={}", dealershipId);
    }

    public void bookingSucceeded(UUID dealershipId, UUID appointmentId) {
        meterRegistry.counter("booking_attempts", "outcome", "success").increment();
        log.info("event=booking.succeeded dealershipId={} appointmentId={}", dealershipId, appointmentId);
    }

    public void bookingConflict(String reason) {
        meterRegistry.counter("booking_attempts", "outcome", "conflict").increment();
        meterRegistry.counter("booking_conflicts", "reason", reason).increment();
        log.warn("event=booking.conflict reason={}", reason);
    }

    public void bookingRejected(String reason) {
        meterRegistry.counter("booking_attempts", "outcome", "rejected").increment();
        log.warn("event=booking.rejected reason={}", reason);
    }

    public void bookingFailed() {
        meterRegistry.counter("booking_attempts", "outcome", "error").increment();
        log.error("event=booking.failed");
    }

    public void dbError(String operation) {
        meterRegistry.counter("db_errors", "operation", operation).increment();
    }
}
