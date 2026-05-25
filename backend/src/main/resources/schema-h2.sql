CREATE TABLE IF NOT EXISTS chat_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    model           VARCHAR(64)  NOT NULL,
    question        CLOB,
    answer          CLOB,
    tool_calls      CLOB,
    latency_ms      BIGINT       NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ARCHIVED',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tenant_config (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id               VARCHAR(64)  NOT NULL UNIQUE,
    model_name              VARCHAR(64)  NOT NULL DEFAULT 'deepseek-chat',
    base_url                VARCHAR(256),
    api_key                 VARCHAR(256),
    temperature             DOUBLE       NOT NULL DEFAULT 0.70,
    max_tokens              INT          NOT NULL DEFAULT 2048,
    function_call_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS gray_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name       VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64),
    target_model    VARCHAR(64)  NOT NULL,
    percentage      INT          NOT NULL DEFAULT 0,
    enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
