package com.cs.customerservice.infrastructure.repository;

import com.cs.customerservice.infrastructure.entity.LogisticsTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LogisticsTraceRepository extends JpaRepository<LogisticsTraceEntity, Long> {
    Optional<LogisticsTraceEntity> findByOrderId(String orderId);
}
