package com.cs.customerservice.infrastructure.repository;

import com.cs.customerservice.infrastructure.entity.AgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<AgentEntity, Long> {

    Optional<AgentEntity> findByUsername(String username);

    Optional<AgentEntity> findByToken(String token);

    List<AgentEntity> findByRoleAndStatus(String role, String status);
}
