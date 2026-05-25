package com.cs.customerservice.infrastructure.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "logistics_traces")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogisticsTraceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(nullable = false, length = 64)
    private String carrier;

    @Column(nullable = false, length = 64)
    private String trackingNo;

    @Column(nullable = false, length = 32)
    private String currentStatus;

    @OneToMany(mappedBy = "trace", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @Builder.Default
    private List<LogisticsTraceNodeEntity> nodes = new ArrayList<>();
}
