-- 对话记录表（对应 ChatRecord 实体）
CREATE TABLE IF NOT EXISTS chat_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    question TEXT,
    answer TEXT,
    tool_calls TEXT,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_session (session_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 租户配置表
CREATE TABLE IF NOT EXISTS tenant_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL UNIQUE,
    model_name VARCHAR(64) NOT NULL DEFAULT 'deepseek-chat',
    base_url VARCHAR(256),
    api_key VARCHAR(256),
    temperature DOUBLE NOT NULL DEFAULT 0.7,
    max_tokens INT NOT NULL DEFAULT 2048,
    function_call_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 灰度规则表
CREATE TABLE IF NOT EXISTS gray_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(64),
    target_model VARCHAR(64) NOT NULL,
    percentage INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
