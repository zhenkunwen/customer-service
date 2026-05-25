package com.cs.customerservice.infrastructure.repository;

import com.cs.customerservice.infrastructure.entity.TransferEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TransferEventRepository extends JpaRepository<TransferEvent, Long> {

    List<TransferEvent> findByTenantIdAndCreatedAtAfter(String tenantId, Instant after);

    List<TransferEvent> findByResolvedFalse();

    @Query("SELECT t.topic, COUNT(t) FROM TransferEvent t WHERE t.createdAt > ?1 GROUP BY t.topic ORDER BY COUNT(t) DESC")
    List<Object[]> countByTopicSince(Instant since);

    long countByTenantIdAndCreatedAtAfter(String tenantId, Instant after);
}
