package com.cs.customerservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentLoginRequest {
    @NotBlank private String username;
    @NotBlank private String password;
}
