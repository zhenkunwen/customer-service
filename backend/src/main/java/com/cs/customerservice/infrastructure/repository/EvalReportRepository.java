package com.cs.customerservice.infrastructure.repository;

import com.cs.customerservice.infrastructure.entity.EvalReportEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvalReportRepository extends JpaRepository<EvalReportEntity, Long> {

    Page<EvalReportEntity> findByTenantIdOrderByEvaluatedAtDesc(String tenantId, Pageable pageable);

    List<EvalReportEntity> findByTenantIdAndIdInOrderByEvaluatedAtDesc(String tenantId, List<Long> ids);
}
