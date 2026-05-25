package com.cs.customerservice.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refund_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String productType;

    @Column(nullable = false)
    private int refundDays;

    @Column(nullable = false, length = 256)
    private String returnConditions;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String policyDetail;
}
