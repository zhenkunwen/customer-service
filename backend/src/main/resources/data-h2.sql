INSERT INTO orders (order_id, user_id, status, amount, product_detail, create_time) VALUES
('ORD-20240001', 'user-001', 'SHIPPED', 299.00, 'Bluetooth Earphone x1', CURRENT_TIMESTAMP);
INSERT INTO orders (order_id, user_id, status, amount, product_detail, create_time) VALUES
('ORD-20240002', 'user-001', 'PENDING', 1599.00, 'Mechanical Keyboard x1', CURRENT_TIMESTAMP);
INSERT INTO orders (order_id, user_id, status, amount, product_detail, create_time) VALUES
('ORD-20240003', 'user-002', 'COMPLETED', 49.90, 'Phone Case x2', CURRENT_TIMESTAMP);

INSERT INTO logistics_traces (order_id, carrier, tracking_no, current_status) VALUES
('ORD-20240001', 'SF Express', 'SF1234567890', 'IN_TRANSIT');

INSERT INTO logistics_trace_nodes (trace_id, event_time, status_desc, location) VALUES
(1, CURRENT_TIMESTAMP, 'Picked up', 'Shanghai Pudong'),
(1, CURRENT_TIMESTAMP, 'In transit', 'Shanghai Sorting Center'),
(1, CURRENT_TIMESTAMP, 'In transit', 'Hangzhou Transfer Station');

INSERT INTO agents (username, password_hash, role, status) VALUES
('admin', '$2b$10$NmT3Jfn6/.PksK.FlpZEs.85BNL2p39kr9h6pZYnq1QkpM7lnbs.u', 'ADMIN', 'OFFLINE');

INSERT INTO refund_policies (product_type, refund_days, return_conditions, policy_detail) VALUES
('ELECTRONICS', 7, 'Original packaging intact', '7-day return, 15-day exchange. Keep all accessories.');
INSERT INTO refund_policies (product_type, refund_days, return_conditions, policy_detail) VALUES
('CLOTHING', 15, 'Tags intact, not washed', '15-day return. Underwear not eligible after opening.');
INSERT INTO refund_policies (product_type, refund_days, return_conditions, policy_detail) VALUES
('FOOD', 7, 'Unopened, within shelf life', '7-day return. Fresh food not eligible after receipt.');
INSERT INTO refund_policies (product_type, refund_days, return_conditions, policy_detail) VALUES
('DAILY', 7, 'Unopened, unused', '7-day return. Personal care items not eligible after opening.');
INSERT INTO refund_policies (product_type, refund_days, return_conditions, policy_detail) VALUES
('GENERAL', 7, 'Item intact with receipt', '7-day no-reason return. Custom items not eligible.');
