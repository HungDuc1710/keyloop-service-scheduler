package com.keyloop.scheduler.service;

import com.keyloop.scheduler.domain.AvailabilityEngine;
import com.keyloop.scheduler.domain.AvailabilityEngine.BusyInterval;
import com.keyloop.scheduler.domain.AvailabilityEngine.Resource;
import com.keyloop.scheduler.domain.AvailabilityEngine.ResourceAssignment;
import com.keyloop.scheduler.domain.BookingException;
import com.keyloop.scheduler.domain.ErrorCode;
import com.keyloop.scheduler.observability.SchedulerMetrics;
import com.keyloop.scheduler.persistence.entity.AppointmentEntity;
import com.keyloop.scheduler.persistence.entity.DealershipEntity;
import com.keyloop.scheduler.persistence.entity.ServiceBayEntity;
import com.keyloop.scheduler.persistence.entity.ServiceTypeEntity;
import com.keyloop.scheduler.persistence.entity.TechnicianEntity;
import com.keyloop.scheduler.persistence.repo.AppointmentRepository;
import com.keyloop.scheduler.persistence.repo.DealershipRepository;
import com.keyloop.scheduler.persistence.repo.DealershipServiceTypeRepository;
import com.keyloop.scheduler.persistence.repo.ServiceBayRepository;
import com.keyloop.scheduler.persistence.repo.ServiceTypeRepository;
import com.keyloop.scheduler.persistence.repo.TechnicianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AvailabilityService {

    private final DealershipRepository dealerships;
    private final ServiceTypeRepository serviceTypes;
    private final DealershipServiceTypeRepository offerings;
    private final ServiceBayRepository bays;
    private final TechnicianRepository technicians;
    private final AppointmentRepository appointments;
    private final Clock clock;
    private final SchedulerMetrics metrics;

    public AvailabilityService(
            DealershipRepository dealerships,
            ServiceTypeRepository serviceTypes,
            DealershipServiceTypeRepository offerings,
            ServiceBayRepository bays,
            TechnicianRepository technicians,
            AppointmentRepository appointments,
            Clock clock,
            SchedulerMetrics metrics
    ) {
        this.dealerships = dealerships;
        this.serviceTypes = serviceTypes;
        this.offerings = offerings;
        this.bays = bays;
        this.technicians = technicians;
        this.appointments = appointments;
        this.clock = clock;
        this.metrics = metrics;
    }

    public record SlotView(Instant startAt, Instant endAt, UUID bayId, String bayName,
                           UUID technicianId, String technicianName) {
    }

    @Transactional(readOnly = true)
    public List<SlotView> slots(UUID dealershipId, UUID serviceTypeId, LocalDate date) {
        long started = System.currentTimeMillis();
        DealershipEntity dealer = dealerships.findById(dealershipId)
                .orElseThrow(() -> new BookingException(ErrorCode.NOT_FOUND, "Dealership not found"));
        ServiceTypeEntity service = serviceTypes.findById(serviceTypeId)
                .orElseThrow(() -> new BookingException(ErrorCode.NOT_FOUND, "Service type not found"));
        requireOffered(dealershipId, serviceTypeId);

        ZoneId zone = ZoneId.of(dealer.getTimezone());
        Instant windowStart = date.atStartOfDay(zone).toInstant();
        Instant windowEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

        Context ctx = loadContext(dealer, service, windowStart, windowEnd);
        Instant now = clock.instant();

        List<SlotView> result = AvailabilityEngine.slotStarts(date, zone, service.getDurationMinutes()).stream()
                .filter(start -> !start.isBefore(now))
                .map(start -> {
                    Instant end = AvailabilityEngine.endAt(start, service.getDurationMinutes());
                    return AvailabilityEngine.assign(ctx.bays(), ctx.techs(), ctx.busy(), start, end, null)
                            .map(a -> new SlotView(start, end, a.bayId(), a.bayName(), a.technicianId(), a.technicianName()));
                })
                .flatMap(Optional::stream)
                .toList();

        metrics.recordAvailability(dealershipId, result.size(), System.currentTimeMillis() - started);
        return result;
    }

    public ResourceAssignment assignOrThrow(DealershipEntity dealer, ServiceTypeEntity service, Instant start) {
        Instant end = AvailabilityEngine.endAt(start, service.getDurationMinutes());
        ZoneId zone = ZoneId.of(dealer.getTimezone());
        AvailabilityEngine.validateWindow(start, end, zone, clock.instant()).ifPresent(code -> {
            throw new BookingException(code, "Requested time is not bookable: " + code.name());
        });

        ZonedDateTime local = start.atZone(zone);
        Instant windowStart = local.toLocalDate().atStartOfDay(zone).toInstant();
        Instant windowEnd = local.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
        Context ctx = loadContext(dealer, service, windowStart, windowEnd);

        long overlapStarted = System.currentTimeMillis();
        Optional<ResourceAssignment> assignment =
                AvailabilityEngine.assign(ctx.bays(), ctx.techs(), ctx.busy(), start, end, null);
        metrics.recordOverlap("bay_and_technician", assignment.isEmpty(), System.currentTimeMillis() - overlapStarted);

        return assignment.orElseThrow(() -> {
            ErrorCode shortage = AvailabilityEngine.classifyShortage(ctx.bays(), ctx.techs(), ctx.busy(), start, end);
            return new BookingException(shortage, "No bay and qualified technician free for the full duration");
        });
    }

    public boolean vehicleOverlaps(UUID dealershipId, UUID vehicleId, Instant start, Instant end) {
        Instant windowStart = start.minusSeconds(12 * 3600);
        Instant windowEnd = end.plusSeconds(12 * 3600);
        List<AppointmentEntity> busy = appointments.findOverlapping(dealershipId, windowStart, windowEnd);
        return busy.stream().anyMatch(a ->
                vehicleId.equals(a.getVehicleId())
                        && AvailabilityEngine.overlaps(a.getStartAt(), a.getEndAt(), start, end));
    }

    private void requireOffered(UUID dealershipId, UUID serviceTypeId) {
        if (!offerings.existsByIdDealershipIdAndIdServiceTypeId(dealershipId, serviceTypeId)) {
            throw new BookingException(ErrorCode.SERVICE_NOT_OFFERED, "Service type is not offered at this dealership");
        }
    }

    private Context loadContext(DealershipEntity dealer, ServiceTypeEntity service, Instant windowStart, Instant windowEnd) {
        List<Resource> bayResources = bays.findByDealershipIdOrderByNameAsc(dealer.getId()).stream()
                .filter(bay -> bay.getCapabilities().contains(service.getRequiredSkill())
                        || bay.getCapabilities().isEmpty())
                .map(bay -> new Resource(bay.getId(), bay.getName()))
                .toList();
        List<Resource> techResources = technicians.findByDealershipIdOrderByNameAsc(dealer.getId()).stream()
                .filter(tech -> tech.getSkills().contains(service.getRequiredSkill()))
                .map(tech -> new Resource(tech.getId(), tech.getName()))
                .sorted(Comparator.comparing(Resource::name))
                .toList();
        List<BusyInterval> busy = appointments.findOverlapping(dealer.getId(), windowStart, windowEnd).stream()
                .map(a -> new BusyInterval(a.getServiceBayId(), a.getTechnicianId(), a.getVehicleId(),
                        a.getStartAt(), a.getEndAt()))
                .toList();
        return new Context(bayResources, techResources, busy);
    }

    private record Context(List<Resource> bays, List<Resource> techs, List<BusyInterval> busy) {
    }
}
