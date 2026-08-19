package com.keyloop.scheduler.persistence.repo;

import com.keyloop.scheduler.persistence.entity.DealershipServiceTypeEntity;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

public interface DealershipServiceTypeRepository
        extends ListCrudRepository<DealershipServiceTypeEntity, DealershipServiceTypeEntity.Id> {

    boolean existsByIdDealershipIdAndIdServiceTypeId(UUID dealershipId, UUID serviceTypeId);

    List<DealershipServiceTypeEntity> findByIdDealershipId(UUID dealershipId);
}
