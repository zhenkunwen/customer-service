package com.cs.customerservice.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatsResponse {
    private long pendingCount;
    private long assignedCount;
    private long inProgressCount;
    private long resolvedCount;
    private long totalCount;
}
