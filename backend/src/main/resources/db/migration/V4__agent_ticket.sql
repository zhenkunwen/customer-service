CREATE TABLE agents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'AGENT',
    status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
    token VARCHAR(128),
    max_concurrent INT NOT NULL DEFAULT 5,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_event_id BIGINT,
    tenant_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    question TEXT,
    emotion_level VARCHAR(8) DEFAULT 'L0',
    topic VARCHAR(128),
    priority INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    assigned_agent_id BIGINT,
    ai_attempted_solutions TEXT,
    resolution TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ticket_status (status),
    INDEX idx_ticket_agent (assigned_agent_id),
    INDEX idx_ticket_tenant (tenant_id)
);

-- 默认管理员（密码 admin123 的 bcrypt hash）
INSERT INTO agents (username, password_hash, role, status)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN', 'OFFLINE');
