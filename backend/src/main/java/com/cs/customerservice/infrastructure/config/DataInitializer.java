package com.cs.customerservice.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        initTenantConfigs();
        initGrayRules();
    }

    private void initTenantConfigs() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_config", Integer.class);
        if (count != null && count > 0) {
            log.info("Tenant config table already populated ({} rows), skipping init", count);
            return;
        }

        log.info("Initializing default tenant configs...");
        List<Object[]> batch = List.of(
                new Object[]{"default",  "deepseek-chat", null, null, 0.70, 2048, true,  true},
                new Object[]{"tenant-a", "deepseek-chat", null, null, 0.50, 1024, true,  true},
                new Object[]{"tenant-b", "deepseek-chat", null, null, 0.80, 4096, true,  true}
        );
        jdbcTemplate.batchUpdate(
                "INSERT INTO tenant_config (tenant_id, model_name, base_url, api_key, temperature, max_tokens, function_call_enabled, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                batch);
        log.info("Inserted {} default tenant configs", batch.size());
    }

    private void initGrayRules() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gray_rule", Integer.class);
        if (count != null && count > 0) {
            log.info("Gray rule table already populated ({} rows), skipping init", count);
            return;
        }

        log.info("Initializing default gray rules...");
        jdbcTemplate.update(
                "INSERT INTO gray_rule (rule_name, tenant_id, target_model, percentage, enabled) VALUES (?, ?, ?, ?, ?)",
                "global-10pct-gpt4", null, "gpt-4o", 10, false);
        log.info("Inserted default gray rule");
    }
}
