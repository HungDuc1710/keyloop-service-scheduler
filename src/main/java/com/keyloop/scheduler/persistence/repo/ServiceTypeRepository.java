package com.keyloop.scheduler.persistence.repo;

import com.keyloop.scheduler.persistence.entity.ServiceTypeEntity;
import org.springframework.data.repository.ListCrudRepository;

import java.util.UUID;

public interface ServiceTypeRepository extends ListCrudRepository<ServiceTypeEntity, UUID> {
}
