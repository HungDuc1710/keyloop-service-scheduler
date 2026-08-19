package com.keyloop.scheduler.api;

import com.keyloop.scheduler.api.dto.AppointmentResponse;
import com.keyloop.scheduler.api.dto.CreateAppointmentRequest;
import com.keyloop.scheduler.api.dto.DealershipResourcesResponse;
import com.keyloop.scheduler.api.dto.DealershipResponse;
import com.keyloop.scheduler.api.dto.SlotResponse;
import com.keyloop.scheduler.domain.BookingException;
import com.keyloop.scheduler.domain.ErrorCode;
import com.keyloop.scheduler.persistence.entity.DealershipEntity;
import com.keyloop.scheduler.persistence.entity.ServiceTypeEntity;
import com.keyloop.scheduler.persistence.repo.DealershipRepository;
import com.keyloop.scheduler.persistence.repo.DealershipServiceTypeRepository;
import com.keyloop.scheduler.persistence.repo.ServiceBayRepository;
import com.keyloop.scheduler.persistence.repo.ServiceTypeRepository;
import com.keyloop.scheduler.persistence.repo.TechnicianRepository;
import com.keyloop.scheduler.service.AvailabilityService;
import com.keyloop.scheduler.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class SchedulerController {

    private final DealershipRepository dealerships;
    private final DealershipServiceTypeRepository offerings;
    private final ServiceTypeRepository serviceTypes;
    private final ServiceBayRepository bays;
    private final TechnicianRepository technicians;
    private final AvailabilityService availabilityService;
    private final BookingService bookingService;

    public SchedulerController(
            DealershipRepository dealerships,
            DealershipServiceTypeRepository offerings,
            ServiceTypeRepository serviceTypes,
            ServiceBayRepository bays,
            TechnicianRepository technicians,
            AvailabilityService availabilityService,
            BookingService bookingService
    ) {
        this.dealerships = dealerships;
        this.offerings = offerings;
        this.serviceTypes = serviceTypes;
        this.bays = bays;
        this.technicians = technicians;
        this.availabilityService = availabilityService;
        this.bookingService = bookingService;
    }

    @GetMapping("/dealerships")
    public List<DealershipResponse> dealerships() {
        return dealerships.findAll().stream()
                .map(d -> new DealershipResponse(d.getId(), d.getName(), d.getTimezone()))
                .toList();
    }

    @GetMapping("/dealerships/{id}/resources")
    public DealershipResourcesResponse resources(@PathVariable UUID id) {
        DealershipEntity dealer = dealerships.findById(id)
                .orElseThrow(() -> new BookingException(ErrorCode.NOT_FOUND, "Dealership not found"));
        Map<UUID, ServiceTypeEntity> types = serviceTypes.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(ServiceTypeEntity::getId, t -> t));
        var serviceViews = offerings.findByIdDealershipId(id).stream()
                .map(link -> types.get(link.getServiceTypeId()))
                .filter(java.util.Objects::nonNull)
                .map(t -> new DealershipResourcesResponse.ServiceTypeView(
                        t.getId(), t.getCode(), t.getName(), t.getDurationMinutes(), t.getRequiredSkill()))
                .toList();
        var bayViews = bays.findByDealershipIdOrderByNameAsc(id).stream()
                .map(b -> new DealershipResourcesResponse.BayView(b.getId(), b.getName(), b.getCapabilities()))
                .toList();
        var techViews = technicians.findByDealershipIdOrderByNameAsc(id).stream()
                .map(t -> new DealershipResourcesResponse.TechnicianView(t.getId(), t.getName(), t.getSkills()))
                .toList();
        return new DealershipResourcesResponse(dealer.getId(), serviceViews, bayViews, techViews);
    }

    @GetMapping("/availability")
    public List<SlotResponse> availability(
            @RequestParam UUID dealershipId,
            @RequestParam UUID serviceTypeId,
            @RequestParam LocalDate date
    ) {
        return availabilityService.slots(dealershipId, serviceTypeId, date).stream()
                .map(s -> new SlotResponse(
                        s.startAt(), s.endAt(), s.bayId(), s.bayName(), s.technicianId(), s.technicianName()))
                .toList();
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResponse> create(
            @Valid @RequestBody CreateAppointmentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        BookingService.BookingOutcome outcome = bookingService.book(request, idempotencyKey);
        return ResponseEntity.status(outcome.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(outcome.body());
    }

    @GetMapping("/appointments/{id}")
    public AppointmentResponse get(@PathVariable UUID id) {
        return bookingService.get(id);
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> list(@RequestParam UUID dealershipId) {
        return bookingService.list(dealershipId);
    }

    @PostMapping("/appointments/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable UUID id) {
        return bookingService.cancel(id);
    }
}
