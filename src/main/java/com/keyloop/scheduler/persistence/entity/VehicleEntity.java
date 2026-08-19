package com.keyloop.scheduler.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class VehicleEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String vin;

    @Column(nullable = false)
    private String registration;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    protected VehicleEntity() {
    }

    public VehicleEntity(UUID id, UUID customerId, String vin, String registration, String make, String model) {
        this.id = id;
        this.customerId = customerId;
        this.vin = vin;
        this.registration = registration;
        this.make = make;
        this.model = model;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getVin() {
        return vin;
    }

    public String getRegistration() {
        return registration;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }
}
