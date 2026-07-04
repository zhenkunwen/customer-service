CREATE TABLE eval_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    evaluated_at TIMESTAMP NOT NULL,
    total_cases INT NOT NULL DEFAULT 0,
    avg_recall DOUBLE,
    avg_precision DOUBLE,
    hit_rate DOUBLE,
    avg_mrr DOUBLE,
    avg_correctness DOUBLE,
    avg_faithfulness DOUBLE,
    avg_relevance DOUBLE,
    details_json MEDIUMTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eval_tenant (tenant_id),
    INDEX idx_eval_time (evaluated_at)
);
