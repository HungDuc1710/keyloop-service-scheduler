package com.keyloop.scheduler.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "service_bays")
public class ServiceBayEntity {

    @Id
    private UUID id;

    @Column(name = "dealership_id", nullable = false)
    private UUID dealershipId;

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bay_capabilities", joinColumns = @JoinColumn(name = "service_bay_id"))
    @Column(name = "capability")
    private Set<String> capabilities = new HashSet<>();

    protected ServiceBayEntity() {
    }

    public ServiceBayEntity(UUID id, UUID dealershipId, String name, Set<String> capabilities) {
        this.id = id;
        this.dealershipId = dealershipId;
        this.name = name;
        this.capabilities = capabilities;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDealershipId() {
        return dealershipId;
    }

    public String getName() {
        return name;
    }

    public Set<String> getCapabilities() {
        return capabilities;
    }
}
