
package com.ledger.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ledger.model.OutboxEvent;
import com.ledger.model.OutboxEvent.Status;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    
    @Query("""
        select e from OutboxEvent e
        where e.status = com.ledger.model.OutboxEvent.Status.PENDING
          and e.availableAt <= :availableAt
        order by e.id
        limit :batchSize
        for update skip locked
        """)
    List<OutboxEvent> lockNextBatch(@Param("availableAt") Instant availableAt,
    	    @Param("batchSize") int batchSize);

    long countByStatus(Status status);

    Optional<OutboxEvent> findFirstByEventTypeOrderByIdDesc(String eventType);
}
