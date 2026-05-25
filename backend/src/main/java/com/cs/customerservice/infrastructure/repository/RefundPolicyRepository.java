package com.cs.customerservice.infrastructure.repository;

import com.cs.customerservice.infrastructure.entity.RefundPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefundPolicyRepository extends JpaRepository<RefundPolicyEntity, Long> {
    Optional<RefundPolicyEntity> findByProductType(String productType);
}
