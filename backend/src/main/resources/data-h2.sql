-- 幂等写入: 文件模式 H2 重启时不会重复插入

-- 管理员账号
INSERT INTO agents (username, password_hash, role, status)
SELECT 'admin', '$2b$10$NmT3Jfn6/.PksK.FlpZEs.85BNL2p39kr9h6pZYnq1QkpM7lnbs.u', 'ADMIN', 'OFFLINE'
WHERE NOT EXISTS (SELECT 1 FROM agents WHERE username = 'admin');

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
