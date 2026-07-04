-- 幂等写入: 文件模式 H2 重启时不会重复插入

-- 管理员账号（主管）
INSERT INTO agents (username, password_hash, role, status)
SELECT 'admin', '$2b$10$NmT3Jfn6/.PksK.FlpZEs.85BNL2p39kr9h6pZYnq1QkpM7lnbs.u', 'TEAM_LEAD', 'OFFLINE'
WHERE NOT EXISTS (SELECT 1 FROM agents WHERE username = 'admin');
UPDATE agents SET role = 'TEAM_LEAD' WHERE username = 'admin' AND role = 'ADMIN';

-- 订单
INSERT INTO orders (order_id, user_id, status, amount, product_detail, create_time)
SELECT 'ORD-20240001', 'user-001', 'SHIPPED', 299.00, 'Bluetooth Earphone x1', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_id = 'ORD-20240001');
INSERT INTO orders (order_id, user_id, status, amount, product_detail, create_time)
SELECT 'ORD-20240002', 'user-001', 'PENDING', 1599.00, 'Mechanical Keyboard x1', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_id = 'ORD-20240002');
INSERT INTO orders (order_id, user_id, status, amount, product_detail, create_time)
SELECT 'ORD-20240003', 'user-002', 'COMPLETED', 49.90, 'Phone Case x2', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_id = 'ORD-20240003');

-- 物流
INSERT INTO logistics_traces (order_id, carrier, tracking_no, current_status)
SELECT 'ORD-20240001', 'SF Express', 'SF1234567890', 'IN_TRANSIT'
WHERE NOT EXISTS (SELECT 1 FROM logistics_traces WHERE order_id = 'ORD-20240001');

-- 物流节点（无唯一键，用组合条件防重复）
INSERT INTO logistics_trace_nodes (trace_id, event_time, status_desc, location)
SELECT 1, CURRENT_TIMESTAMP, 'Picked up', 'Shanghai Pudong'
WHERE NOT EXISTS (SELECT 1 FROM logistics_trace_nodes WHERE trace_id = 1 AND status_desc = 'Picked up' AND location = 'Shanghai Pudong');
INSERT INTO logistics_trace_nodes (trace_id, event_time, status_desc, location)
SELECT 1, CURRENT_TIMESTAMP, 'In transit', 'Shanghai Sorting Center'
WHERE NOT EXISTS (SELECT 1 FROM logistics_trace_nodes WHERE trace_id = 1 AND status_desc = 'In transit' AND location = 'Shanghai Sorting Center');
INSERT INTO logistics_trace_nodes (trace_id, event_time, status_desc, location)
SELECT 1, CURRENT_TIMESTAMP, 'In transit', 'Hangzhou Transfer Station'
WHERE NOT EXISTS (SELECT 1 FROM logistics_trace_nodes WHERE trace_id = 1 AND status_desc = 'In transit' AND location = 'Hangzhou Transfer Station');

-- 退款策略
INSERT INTO refund_policies (product_type, refund_days, return_conditions, policy_detail)
SELECT 'ELECTRONICS', 7, 'Original packaging intact', '7-day return, 15-day exchange. Keep all accessories.'
WHERE NOT EXISTS (SELECT 1 FROM refund_policies WHERE product_type = 'ELECTRONICS');
INSERT INTO refund_policies (product_type, refund_days, return_conditions, policy_detail)
SELECT 'CLOTHING', 15, 'Tags intact, not washed', '15-day return. Underwear not eligible after opening.'
WHERE NOT EXISTS (SELECT 1 FROM refund_policies WHERE product_type = 'CLOTHING');
INSERT INTO refund_policies (product_type, refund_days, return_conditions, policy_detail)
SELECT 'FOOD', 7, 'Unopened, within shelf life', '7-day return. Fresh food not eligible after receipt.'
WHERE NOT EXISTS (SELECT 1 FROM refund_policies WHERE product_type = 'FOOD');
INSERT INTO refund_policies (product_type, refund_days, return_conditions, policy_detail)
SELECT 'DAILY', 7, 'Unopened, unused', '7-day return. Personal care items not eligible after opening.'
WHERE NOT EXISTS (SELECT 1 FROM refund_policies WHERE product_type = 'DAILY');
INSERT INTO refund_policies (product_type, refund_days, return_conditions, policy_detail)
SELECT 'GENERAL', 7, 'Item intact with receipt', '7-day no-reason return. Custom items not eligible.'
WHERE NOT EXISTS (SELECT 1 FROM refund_policies WHERE product_type = 'GENERAL');

-- 示例对话记录（供评估数据集生成使用）
INSERT INTO chat_records (session_id, tenant_id, user_id, model, question, answer, latency_ms, status, created_at)
SELECT 'eval-session-001', 'default', 'u1', 'deepseek-chat', '商品怎么退货？', '您好，7天内可以无理由退货。电子产品需要保持原包装完整，衣物要保持吊牌未拆洗。请问您购买的是什么类型的商品？', 500, 'ARCHIVED', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM chat_records WHERE session_id = 'eval-session-001');
INSERT INTO chat_records (session_id, tenant_id, user_id, model, question, answer, latency_ms, status, created_at)
SELECT 'eval-session-002', 'default', 'u2', 'deepseek-chat', '快递太慢了，都五天了', '抱歉让您久等了。请提供您的订单号，我帮您查询物流状态。', 300, 'ARCHIVED', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM chat_records WHERE session_id = 'eval-session-002');
INSERT INTO chat_records (session_id, tenant_id, user_id, model, question, answer, latency_ms, status, created_at)
SELECT 'eval-session-003', 'default', 'u3', 'deepseek-chat', '怎么使用优惠券', '在结算页面可以选择优惠券。您可以在"我的优惠券"中查看可用的优惠券，选择后会自动抵扣金额。', 400, 'ARCHIVED', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM chat_records WHERE session_id = 'eval-session-003');
INSERT INTO chat_records (session_id, tenant_id, user_id, model, question, answer, latency_ms, status, created_at)
SELECT 'eval-session-004', 'default', 'u4', 'deepseek-chat', '怎么联系人工客服', '您可以直接在此对话中说明您的问题，我会先为您处理。如果问题较复杂，我们可以为您转接人工客服。', 350, 'ARCHIVED', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM chat_records WHERE session_id = 'eval-session-004');
INSERT INTO chat_records (session_id, tenant_id, user_id, model, question, answer, latency_ms, status, created_at)
SELECT 'eval-session-005', 'default', 'u5', 'deepseek-chat', '我的订单怎么还没到', '请提供您的订单号，我帮您查询物流状态。正常情况下，发货后3-5个工作日送达。', 450, 'ARCHIVED', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM chat_records WHERE session_id = 'eval-session-005');
