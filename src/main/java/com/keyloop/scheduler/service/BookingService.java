package com.keyloop.scheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyloop.scheduler.api.dto.AppointmentResponse;
import com.keyloop.scheduler.api.dto.CreateAppointmentRequest;
import com.keyloop.scheduler.domain.AvailabilityEngine;
import com.keyloop.scheduler.domain.AvailabilityEngine.ResourceAssignment;
import com.keyloop.scheduler.domain.BookingException;
import com.keyloop.scheduler.domain.ErrorCode;
import com.keyloop.scheduler.observability.SchedulerMetrics;
import com.keyloop.scheduler.persistence.entity.AppointmentEntity;
import com.keyloop.scheduler.persistence.entity.CustomerEntity;
import com.keyloop.scheduler.persistence.entity.DealershipEntity;
import com.keyloop.scheduler.persistence.entity.IdempotencyKeyEntity;
import com.keyloop.scheduler.persistence.entity.ServiceBayEntity;
import com.keyloop.scheduler.persistence.entity.ServiceTypeEntity;
import com.keyloop.scheduler.persistence.entity.TechnicianEntity;
import com.keyloop.scheduler.persistence.entity.VehicleEntity;
import com.keyloop.scheduler.persistence.repo.AppointmentRepository;
import com.keyloop.scheduler.persistence.repo.CustomerRepository;
import com.keyloop.scheduler.persistence.repo.DealershipRepository;
import com.keyloop.scheduler.persistence.repo.DealershipServiceTypeRepository;
import com.keyloop.scheduler.persistence.repo.IdempotencyKeyRepository;
import com.keyloop.scheduler.persistence.repo.ServiceBayRepository;
import com.keyloop.scheduler.persistence.repo.ServiceTypeRepository;
import com.keyloop.scheduler.persistence.repo.TechnicianRepository;
import com.keyloop.scheduler.persistence.repo.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {

    private final DealershipRepository dealerships;
    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final ServiceTypeRepository serviceTypes;
    private final DealershipServiceTypeRepository offerings;
    private final ServiceBayRepository bays;
    private final TechnicianRepository technicians;
    private final AppointmentRepository appointments;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final AvailabilityService availabilityService;
    private final Clock clock;
    private final SchedulerMetrics metrics;
    private final ObjectMapper objectMapper;

    public BookingService(
            DealershipRepository dealerships,
            CustomerRepository customers,
            VehicleRepository vehicles,
            ServiceTypeRepository serviceTypes,
            DealershipServiceTypeRepository offerings,
            ServiceBayRepository bays,
            TechnicianRepository technicians,
            AppointmentRepository appointments,
            IdempotencyKeyRepository idempotencyKeys,
            AvailabilityService availabilityService,
            Clock clock,
            SchedulerMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.dealerships = dealerships;
        this.customers = customers;
        this.vehicles = vehicles;
        this.serviceTypes = serviceTypes;
        this.offerings = offerings;
        this.bays = bays;
        this.technicians = technicians;
        this.appointments = appointments;
        this.idempotencyKeys = idempotencyKeys;
        this.availabilityService = availabilityService;
        this.clock = clock;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    public record BookingOutcome(AppointmentResponse body, int statusCode, boolean replay) {
    }

    @Transactional
    public BookingOutcome book(CreateAppointmentRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BookingException(ErrorCode.MISSING_IDEMPOTENCY_KEY, "Idempotency-Key header is required");
        }

        String hash = hashRequest(request);
        Optional<IdempotencyKeyEntity> existing = idempotencyKeys.findById(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyKeyEntity row = existing.get();
            if (!row.getRequestHash().equals(hash)) {
                throw new BookingException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency-Key was already used with a different request body");
            }
            try {
                AppointmentResponse replay = objectMapper.readValue(row.getResponseJson(), AppointmentResponse.class);
                return new BookingOutcome(replay, row.getStatusCode(), true);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Corrupt idempotency payload", e);
            }
        }

        metrics.bookingAttempted(request.dealershipId());

        DealershipEntity dealer = dealerships.lockById(request.dealershipId())
                .orElseThrow(() -> notFound("Dealership"));
        CustomerEntity customer = customers.findById(request.customerId())
                .orElseThrow(() -> notFound("Customer"));
        VehicleEntity vehicle = vehicles.findById(request.vehicleId())
                .orElseThrow(() -> notFound("Vehicle"));
        ServiceTypeEntity service = serviceTypes.findById(request.serviceTypeId())
                .orElseThrow(() -> notFound("Service type"));

        if (!vehicle.getCustomerId().equals(customer.getId())) {
            metrics.bookingRejected(ErrorCode.VEHICLE_NOT_OWNED.name());
            throw new BookingException(ErrorCode.VEHICLE_NOT_OWNED, "Vehicle does not belong to the customer");
        }
        if (!offerings.existsByIdDealershipIdAndIdServiceTypeId(dealer.getId(), service.getId())) {
            metrics.bookingRejected(ErrorCode.SERVICE_NOT_OFFERED.name());
            throw new BookingException(ErrorCode.SERVICE_NOT_OFFERED, "Service type is not offered at this dealership");
        }

        Instant start = request.startAt().toInstant();
        Instant end = AvailabilityEngine.endAt(start, service.getDurationMinutes());

        if (availabilityService.vehicleOverlaps(dealer.getId(), vehicle.getId(), start, end)) {
            metrics.bookingConflict("vehicle_overlap");
            throw new BookingException(ErrorCode.VEHICLE_OVERLAP, "Vehicle already has a confirmed overlapping appointment");
        }

        ResourceAssignment assignment;
        try {
            assignment = availabilityService.assignOrThrow(dealer, service, start);
        } catch (BookingException ex) {
            if (ex.code().status().is4xxClientError() && ex.code() != ErrorCode.NOT_FOUND) {
                if (ex.code() == ErrorCode.NOT_ON_GRID || ex.code() == ErrorCode.VEHICLE_NOT_OWNED
                        || ex.code() == ErrorCode.SERVICE_NOT_OFFERED) {
                    metrics.bookingRejected(ex.code().name());
                } else {
                    metrics.bookingConflict(ex.code().name().toLowerCase());
                }
            }
            throw ex;
        }

        ServiceBayEntity bay = bays.findById(assignment.bayId()).orElseThrow();
        TechnicianEntity tech = technicians.findById(assignment.technicianId()).orElseThrow();

        AppointmentEntity saved = appointments.save(new AppointmentEntity(
                UUID.randomUUID(),
                dealer.getId(),
                customer.getId(),
                vehicle.getId(),
                service.getId(),
                tech.getId(),
                bay.getId(),
                start,
                end,
                "CONFIRMED"
        ));

        AppointmentResponse body = toResponse(saved, dealer, customer, vehicle, service, bay, tech);
        try {
            idempotencyKeys.save(new IdempotencyKeyEntity(
                    idempotencyKey,
                    hash,
                    saved.getId(),
                    201,
                    objectMapper.writeValueAsString(body),
                    clock.instant()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }

        metrics.bookingSucceeded(dealer.getId(), saved.getId());
        return new BookingOutcome(body, 201, false);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID id) {
        AppointmentEntity appointment = appointments.findById(id)
                .orElseThrow(() -> notFound("Appointment"));
        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(UUID dealershipId) {
        dealerships.findById(dealershipId).orElseThrow(() -> notFound("Dealership"));
        return appointments.findByDealershipIdOrderByStartAtAsc(dealershipId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AppointmentResponse cancel(UUID id) {
        AppointmentEntity appointment = appointments.findById(id)
                .orElseThrow(() -> notFound("Appointment"));
        if (appointment.getCancelledAt() == null) {
            appointment.cancel(clock.instant());
        }
        return toResponse(appointment);
    }

    private AppointmentResponse toResponse(AppointmentEntity appointment) {
        DealershipEntity dealer = dealerships.findById(appointment.getDealershipId()).orElseThrow();
        CustomerEntity customer = customers.findById(appointment.getCustomerId()).orElseThrow();
        VehicleEntity vehicle = vehicles.findById(appointment.getVehicleId()).orElseThrow();
        ServiceTypeEntity service = serviceTypes.findById(appointment.getServiceTypeId()).orElseThrow();
        ServiceBayEntity bay = bays.findById(appointment.getServiceBayId()).orElseThrow();
        TechnicianEntity tech = technicians.findById(appointment.getTechnicianId()).orElseThrow();
        return toResponse(appointment, dealer, customer, vehicle, service, bay, tech);
    }

    private AppointmentResponse toResponse(
            AppointmentEntity appointment,
            DealershipEntity dealer,
            CustomerEntity customer,
            VehicleEntity vehicle,
            ServiceTypeEntity service,
            ServiceBayEntity bay,
            TechnicianEntity tech
    ) {
        return new AppointmentResponse(
                appointment.getId(),
                dealer.getId(),
                dealer.getName(),
                customer.getId(),
                customer.getName(),
                vehicle.getId(),
                vehicle.getRegistration(),
                service.getId(),
                service.getCode(),
                bay.getId(),
                bay.getName(),
                tech.getId(),
                tech.getName(),
                appointment.getStartAt(),
                appointment.getEndAt(),
                appointment.getStatus()
        );
    }

    private static BookingException notFound(String what) {
        return new BookingException(ErrorCode.NOT_FOUND, what + " not found");
    }

    private String hashRequest(CreateAppointmentRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
