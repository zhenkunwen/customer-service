package com.cs.customerservice.infrastructure.repository;

import com.cs.customerservice.infrastructure.entity.TicketEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<TicketEntity, Long> {

    Page<TicketEntity> findByStatusAndTenantId(String status, String tenantId, Pageable pageable);

    Page<TicketEntity> findByAssignedAgentId(Long agentId, Pageable pageable);

    List<TicketEntity> findByStatusOrderByPriorityDescCreatedAtAsc(String status);

    @Query("SELECT t.status, COUNT(t) FROM TicketEntity t GROUP BY t.status")
    List<Object[]> countByStatus();

    long countByStatus(String status);

    long countByAssignedAgentIdAndStatusIn(Long agentId, List<String> statuses);

    @Query("SELECT t.assignedAgentId, COUNT(t) FROM TicketEntity t " +
           "WHERE t.assignedAgentId IN :agentIds AND t.status IN ('ASSIGNED', 'IN_PROGRESS') GROUP BY t.assignedAgentId")
    List<Object[]> countAssignedByAgentIds(@Param("agentIds") List<Long> agentIds);
}
