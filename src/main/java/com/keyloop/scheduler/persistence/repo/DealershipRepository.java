package com.keyloop.scheduler.persistence.repo;

import com.keyloop.scheduler.persistence.entity.DealershipEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DealershipRepository extends ListCrudRepository<DealershipEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DealershipEntity d where d.id = :id")
    Optional<DealershipEntity> lockById(@Param("id") UUID id);
}
