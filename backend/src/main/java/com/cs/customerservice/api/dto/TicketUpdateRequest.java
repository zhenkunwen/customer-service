package com.cs.customerservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketUpdateRequest {
    @NotBlank private String resolution;
}
