-- ============================================================
-- 智能客服系统 — 数据库初始化脚本
-- ============================================================

CREATE DATABASE IF NOT EXISTS cs_customer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cs_customer;

-- -----------------------------------------------------------
-- 1. 会话归档表（Kafka 消费者写入）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64)     NOT NULL COMMENT '会话ID',
    tenant_id       VARCHAR(64)     NOT NULL COMMENT '租户ID',
    user_id         VARCHAR(64)     NOT NULL COMMENT '用户ID',
    model           VARCHAR(64)     NOT NULL COMMENT '模型名称',
    question        TEXT            NULL     COMMENT '用户问题',
    answer          TEXT            NULL     COMMENT '客服回答',
    tool_calls      TEXT            NULL     COMMENT '工具调用JSON',
    latency_ms      BIGINT          NOT NULL DEFAULT 0 COMMENT '耗时(ms)',
    status          VARCHAR(32)     NOT NULL DEFAULT 'ARCHIVED' COMMENT '状态',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

    INDEX idx_session   (session_id),
    INDEX idx_tenant    (tenant_id),
    INDEX idx_created   (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话归档表';

-- -----------------------------------------------------------
-- 2. 租户模型配置表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenant_config (
    id                      BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id               VARCHAR(64)     NOT NULL UNIQUE COMMENT '租户标识',
    model_name              VARCHAR(64)     NOT NULL DEFAULT 'deepseek-chat' COMMENT '模型名称',
    base_url                VARCHAR(256)    NULL     COMMENT '自定义 API 地址（NULL表示用默认）',
    api_key                 VARCHAR(256)    NULL     COMMENT '自定义 API Key（NULL表示用默认）',
    temperature             DECIMAL(3,2)    NOT NULL DEFAULT 0.70 COMMENT '温度参数',
    max_tokens              INT             NOT NULL DEFAULT 2048 COMMENT '最大输出token',
    function_call_enabled   TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用工具调用',
    enabled                 TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用该租户',
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    INDEX idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户模型配置表';

-- -----------------------------------------------------------
-- 3. 灰度规则表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS gray_rule (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rule_name       VARCHAR(64)     NOT NULL COMMENT '规则名称',
    tenant_id       VARCHAR(64)     NULL     COMMENT '限定租户（NULL表示全局）',
    target_model    VARCHAR(64)     NOT NULL COMMENT '灰度目标模型',
    percentage      INT             NOT NULL DEFAULT 0 COMMENT '灰度比例 0-100',
    enabled         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否启用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    INDEX idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='灰度规则表';

-- -----------------------------------------------------------
-- 默认租户数据
-- -----------------------------------------------------------
INSERT INTO tenant_config (tenant_id, model_name, temperature, max_tokens, function_call_enabled, enabled)
VALUES
    ('default',   'deepseek-chat', 0.70, 2048, 1, 1),
    ('tenant-a',  'deepseek-chat', 0.50, 1024, 1, 1),
    ('tenant-b',  'deepseek-chat', 0.80, 4096, 1, 1)
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO gray_rule (rule_name, tenant_id, target_model, percentage, enabled)
VALUES
    ('global-10pct-gpt4', NULL, 'gpt-4o', 10, 0)
ON DUPLICATE KEY UPDATE updated_at = NOW();
