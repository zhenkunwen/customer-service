-- 转人工事件记录表，用于闭环学习：聚类未解决问题、统计高频转人工话题
CREATE TABLE transfer_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    question TEXT COMMENT '用户最后的问题',
    emotion_level VARCHAR(8) DEFAULT 'L0' COMMENT '情绪等级 L0-L3',
    topic VARCHAR(128) COMMENT '问题分类：物流/退货/订单/优惠/账户/库存/其他',
    attempted_solutions TEXT COMMENT 'AI尝试过的解决方案简述',
    resolution TEXT COMMENT '人工客服的解决方案（由人工填写，用于学习）',
    resolved TINYINT(1) DEFAULT 0 COMMENT '是否已由人工解决',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_topic (tenant_id, topic),
    INDEX idx_created (created_at),
    INDEX idx_unresolved (resolved, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转人工事件记录，用于未解决问题聚类';
