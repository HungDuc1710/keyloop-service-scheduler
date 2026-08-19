package com.keyloop.scheduler.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class AppointmentEntity {

    @Id
    private UUID id;

    @Column(name = "dealership_id", nullable = false)
    private UUID dealershipId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "service_bay_id", nullable = false)
    private UUID serviceBayId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected AppointmentEntity() {
    }

    public AppointmentEntity(
            UUID id,
            UUID dealershipId,
            UUID customerId,
            UUID vehicleId,
            UUID serviceTypeId,
            UUID technicianId,
            UUID serviceBayId,
            Instant startAt,
            Instant endAt,
            String status
    ) {
        this.id = id;
        this.dealershipId = dealershipId;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.serviceTypeId = serviceTypeId;
        this.technicianId = technicianId;
        this.serviceBayId = serviceBayId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDealershipId() {
        return dealershipId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getServiceTypeId() {
        return serviceTypeId;
    }

    public UUID getTechnicianId() {
        return technicianId;
    }

    public UUID getServiceBayId() {
        return serviceBayId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void cancel(Instant at) {
        this.status = "CANCELLED";
        this.cancelledAt = at;
    }
}
