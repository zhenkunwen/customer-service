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

CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        VARCHAR(64)  NOT NULL UNIQUE,
    user_id         VARCHAR(64)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    product_detail  VARCHAR(512),
    create_time     DATETIME     NOT NULL
);

CREATE TABLE IF NOT EXISTS logistics_traces (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        VARCHAR(64)  NOT NULL UNIQUE,
    carrier         VARCHAR(64)  NOT NULL,
    tracking_no     VARCHAR(64)  NOT NULL,
    current_status  VARCHAR(32)  NOT NULL
);

CREATE TABLE IF NOT EXISTS logistics_trace_nodes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        BIGINT       NOT NULL,
    event_time      DATETIME     NOT NULL,
    status_desc     VARCHAR(128) NOT NULL,
    location        VARCHAR(128),
    FOREIGN KEY (trace_id) REFERENCES logistics_traces(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS agents (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(256) NOT NULL,
    role            VARCHAR(16)  NOT NULL DEFAULT 'AGENT',
    status          VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE',
    token           VARCHAR(128),
    max_concurrent  INT          NOT NULL DEFAULT 5,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tickets (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_event_id       BIGINT,
    tenant_id               VARCHAR(64)  NOT NULL,
    session_id              VARCHAR(64)  NOT NULL,
    question                CLOB,
    emotion_level           VARCHAR(8)   DEFAULT 'L0',
    topic                   VARCHAR(128),
    priority                INT          NOT NULL DEFAULT 0,
    status                  VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    assigned_agent_id       BIGINT,
    ai_attempted_solutions  CLOB,
    resolution              CLOB,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ticket_status ON tickets(status);
CREATE INDEX IF NOT EXISTS idx_ticket_agent ON tickets(assigned_agent_id);
CREATE INDEX IF NOT EXISTS idx_ticket_tenant ON tickets(tenant_id);

CREATE TABLE IF NOT EXISTS refund_policies (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_type    VARCHAR(64)  NOT NULL UNIQUE,
    refund_days     INT          NOT NULL,
    return_conditions VARCHAR(256) NOT NULL,
    policy_detail   CLOB         NOT NULL
);
