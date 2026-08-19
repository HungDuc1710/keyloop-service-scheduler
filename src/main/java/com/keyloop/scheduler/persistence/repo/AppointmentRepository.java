package com.keyloop.scheduler.persistence.repo;

import com.keyloop.scheduler.persistence.entity.AppointmentEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends ListCrudRepository<AppointmentEntity, UUID> {

    List<AppointmentEntity> findByDealershipIdOrderByStartAtAsc(UUID dealershipId);

    @Query("""
            select a from AppointmentEntity a
            where a.dealershipId = :dealershipId
              and a.cancelledAt is null
              and a.startAt < :end
              and a.endAt > :start
            """)
    List<AppointmentEntity> findOverlapping(
            @Param("dealershipId") UUID dealershipId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
