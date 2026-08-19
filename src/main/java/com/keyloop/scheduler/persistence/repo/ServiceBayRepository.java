package com.keyloop.scheduler.persistence.repo;

import com.keyloop.scheduler.persistence.entity.ServiceBayEntity;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceBayRepository extends ListCrudRepository<ServiceBayEntity, UUID> {
    List<ServiceBayEntity> findByDealershipIdOrderByNameAsc(UUID dealershipId);
}
