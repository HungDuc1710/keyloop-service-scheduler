package com.keyloop.scheduler.persistence.repo;

import com.keyloop.scheduler.persistence.entity.CustomerEntity;
import org.springframework.data.repository.ListCrudRepository;

import java.util.UUID;

public interface CustomerRepository extends ListCrudRepository<CustomerEntity, UUID> {
}
