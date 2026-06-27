package com.cs.customerservice.infrastructure.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRecordRepository extends JpaRepository<ChatRecord, Long> {
    List<ChatRecord> findBySessionIdOrderByCreatedAtDesc(String sessionId);
    List<ChatRecord> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    List<ChatRecord> findByTenantId(String tenantId);
}
