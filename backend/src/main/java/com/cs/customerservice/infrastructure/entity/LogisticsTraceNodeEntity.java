package com.cs.customerservice.infrastructure.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "logistics_trace_nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogisticsTraceNodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trace_id", nullable = false)
    @JsonIgnore
    private LogisticsTraceEntity trace;

    @Column(nullable = false)
    private Instant eventTime;

    @Column(nullable = false, length = 128)
    private String statusDesc;

    @Column(length = 128)
    private String location;
}
