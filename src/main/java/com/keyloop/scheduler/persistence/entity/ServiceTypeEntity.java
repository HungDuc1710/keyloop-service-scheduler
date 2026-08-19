package com.keyloop.scheduler.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "service_types")
public class ServiceTypeEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "required_skill", nullable = false)
    private String requiredSkill;

    protected ServiceTypeEntity() {
    }

    public ServiceTypeEntity(UUID id, String code, String name, int durationMinutes, String requiredSkill) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.requiredSkill = requiredSkill;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getRequiredSkill() {
        return requiredSkill;
    }
}
