package com.keyloop.scheduler.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "dealership_service_types")
public class DealershipServiceTypeEntity {

    @EmbeddedId
    private Id id;

    protected DealershipServiceTypeEntity() {
    }

    public DealershipServiceTypeEntity(UUID dealershipId, UUID serviceTypeId) {
        this.id = new Id(dealershipId, serviceTypeId);
    }

    public UUID getDealershipId() {
        return id.dealershipId;
    }

    public UUID getServiceTypeId() {
        return id.serviceTypeId;
    }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "dealership_id")
        private UUID dealershipId;
        @Column(name = "service_type_id")
        private UUID serviceTypeId;

        protected Id() {
        }

        public Id(UUID dealershipId, UUID serviceTypeId) {
            this.dealershipId = dealershipId;
            this.serviceTypeId = serviceTypeId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Id other)) {
                return false;
            }
            return Objects.equals(dealershipId, other.dealershipId)
                    && Objects.equals(serviceTypeId, other.serviceTypeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dealershipId, serviceTypeId);
        }
    }
}
