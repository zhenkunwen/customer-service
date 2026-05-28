package com.cs.customerservice.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLoadResponse {
    private Long id;
    private String username;
    private String role;
    private String status;
    private long currentLoad;
}
