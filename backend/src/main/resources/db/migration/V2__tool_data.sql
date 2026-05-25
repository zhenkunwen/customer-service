-- 订单表（替代 OrderTool 中的硬编码 Map）
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    product_detail VARCHAR(512),
    create_time DATETIME(3) NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 物流追踪表（替代 LogisticsTool 中的硬编码 Map）
CREATE TABLE IF NOT EXISTS logistics_traces (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    carrier VARCHAR(64) NOT NULL,
    tracking_no VARCHAR(64) NOT NULL,
    current_status VARCHAR(32) NOT NULL,
    INDEX idx_logistics_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 物流追踪节点表
CREATE TABLE IF NOT EXISTS logistics_trace_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id BIGINT NOT NULL,
    event_time DATETIME(3) NOT NULL,
    status_desc VARCHAR(128) NOT NULL,
    location VARCHAR(128),
    FOREIGN KEY (trace_id) REFERENCES logistics_traces(id) ON DELETE CASCADE,
    INDEX idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 退货政策表（替代 RefundTool 中的硬编码 Map）
CREATE TABLE IF NOT EXISTS refund_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_type VARCHAR(64) NOT NULL UNIQUE,
    refund_days INT NOT NULL,
    return_conditions VARCHAR(256) NOT NULL,
    policy_detail TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入种子数据
INSERT IGNORE INTO orders (order_id, user_id, status, amount, product_detail, create_time) VALUES
('ORD-20240001', 'user-001', '已发货', 299.00, '商品：蓝牙耳机 x1，收货地址：上海市浦东新区', NOW() - INTERVAL 2 DAY),
('ORD-20240002', 'user-001', '待付款', 1599.00, '商品：机械键盘 x1，请在30分钟内完成支付', NOW() - INTERVAL 3 HOUR),
('ORD-20240003', 'user-002', '已完成', 49.90, '商品：手机壳 x2，已签收', NOW() - INTERVAL 10 DAY);

INSERT IGNORE INTO logistics_traces (order_id, carrier, tracking_no, current_status) VALUES
('ORD-20240001', '顺丰速运', 'SF1234567890', '运输中');

INSERT IGNORE INTO logistics_trace_nodes (trace_id, event_time, status_desc, location) VALUES
(1, NOW() - INTERVAL 2 DAY, '已揽收', '上海市浦东新区XX营业点'),
(1, NOW() - INTERVAL 1 DAY, '运输中', '上海分拣中心'),
(1, NOW(), '运输中', '杭州市中转站');

INSERT IGNORE INTO refund_policies (product_type, refund_days, return_conditions, policy_detail) VALUES
('电子产品', 7, '包装完好、配件齐全、无人为损坏', '自签收之日起7天内可申请退货，15天内可换货。需保留原包装及所有配件，激活后不支持退货。'),
('服饰', 15, '吊牌完好、未洗涤、无污渍', '自签收之日起15天内可申请退货。内衣、袜子等贴身衣物拆封后不支持退货。'),
('食品', 7, '未拆封、保质期内', '自签收之日起7天内可申请退货。生鲜类商品签收后不支持退货，如有质量问题请拍照联系客服。'),
('日用品', 7, '未拆封、未使用', '自签收之日起7天内可申请退货。个人护理类商品拆封后不支持退货。'),
('通用', 7, '商品完好、附购买凭证', '自签收之日起7天内可申请无理由退货。特殊商品（定制类、虚拟商品等）不支持退货，详见商品详情页。');
