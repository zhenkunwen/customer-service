package com.cs.customerservice.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cs.degradation")
public class DegradationConfig {

    private static final Logger log = LoggerFactory.getLogger(DegradationConfig.class);

    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            log.warn("Degradation mode toggled: {} -> {}", this.enabled, enabled);
        }
        this.enabled = enabled;
    }
}
