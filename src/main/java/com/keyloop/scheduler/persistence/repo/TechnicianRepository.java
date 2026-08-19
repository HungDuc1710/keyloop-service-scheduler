package com.keyloop.scheduler.persistence.repo;

import com.keyloop.scheduler.persistence.entity.TechnicianEntity;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

public interface TechnicianRepository extends ListCrudRepository<TechnicianEntity, UUID> {
    List<TechnicianEntity> findByDealershipIdOrderByNameAsc(UUID dealershipId);
}
