package com.keyloop.scheduler.persistence.repo;

import com.keyloop.scheduler.persistence.entity.IdempotencyKeyEntity;
import org.springframework.data.repository.ListCrudRepository;

public interface IdempotencyKeyRepository extends ListCrudRepository<IdempotencyKeyEntity, String> {
}
