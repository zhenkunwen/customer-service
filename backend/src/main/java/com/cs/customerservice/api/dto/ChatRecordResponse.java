package com.cs.customerservice.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRecordResponse {
    private Long id;
    private String userId;
    private String model;
    private String question;
    private String answer;
    private Long latencyMs;
    private String status;
    private Instant createdAt;
}
