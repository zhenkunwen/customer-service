package com.cs.customerservice.application.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class MallApiClient {

    private static final Logger log = LoggerFactory.getLogger(MallApiClient.class);
    private final RestClient restClient;

    public MallApiClient() {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8081")
                .build();
    }

    public boolean createReturnApply(String orderSn, String reason, String memberUsername) {
        try {
            Map<String, Object> body = Map.of(
                    "orderSn", orderSn,
                    "reason", reason,
                    "memberUsername", memberUsername,
                    "returnName", memberUsername,
                    "returnPhone", ""
            );
            var resp = restClient.post()
                    .uri("/returnApply/create")
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);
            log.info("Mall return apply created: orderSn={}, status={}", orderSn, resp.getStatusCode());
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Mall API call failed: {}", e.getMessage());
            return false;
        }
    }
}
