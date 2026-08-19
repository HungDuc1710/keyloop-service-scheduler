package com.keyloop.scheduler.persistence.repo;

import com.keyloop.scheduler.persistence.entity.VehicleEntity;
import org.springframework.data.repository.ListCrudRepository;

import java.util.UUID;

public interface VehicleRepository extends ListCrudRepository<VehicleEntity, UUID> {
}
