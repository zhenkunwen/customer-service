package com.cs.customerservice.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "cs.security")
public class SecurityProperties {

    private Map<String, String> tenantKeys = Map.of(
            "default", "change-me",
            "tenant-a", "change-me",
            "tenant-b", "change-me"
    );

    public Map<String, String> getTenantKeys() { return tenantKeys; }
    public void setTenantKeys(Map<String, String> tenantKeys) { this.tenantKeys = tenantKeys; }
}
