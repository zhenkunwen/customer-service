package com.cs.customerservice.application.tool;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MallOrderReader {

    private static final Logger log = LoggerFactory.getLogger(MallOrderReader.class);
    private final JdbcTemplate jdbc;

    public MallOrderReader() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/mall?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        config.setUsername("root");
        config.setPassword("123456");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(3000);
        this.jdbc = new JdbcTemplate(new HikariDataSource(config));
    }

    public Optional<Map<String, Object>> findOrderBySn(String orderSn) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT o.*, m.username AS member_username, m.phone AS member_phone " +
                    "FROM oms_order o LEFT JOIN ums_member m ON o.member_id = m.id " +
                    "WHERE o.order_sn = ?", orderSn);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        } catch (Exception e) {
            log.warn("Mall DB query order failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<Map<String, Object>> findOrderHistory(Long orderId) {
        try {
            return jdbc.queryForList(
                    "SELECT * FROM oms_order_operate_history WHERE order_id = ? ORDER BY create_time DESC", orderId);
        } catch (Exception e) {
            log.warn("Mall DB query history failed: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> findReturnReasons() {
        try {
            return jdbc.queryForList(
                    "SELECT * FROM oms_order_return_reason WHERE status = 1 ORDER BY sort");
        } catch (Exception e) {
            log.warn("Mall DB query return reasons failed: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> queryProducts(String q, String sort, String cat,
                                                    Double minPrice, Double maxPrice) {
        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM pms_product WHERE delete_status = 0");
            if (q != null && !q.isBlank()) sql.append(" AND name LIKE '%").append(q.replace("'","''")).append("%'");
            if (cat != null && !cat.isBlank()) sql.append(" AND product_category_name = '").append(cat.replace("'","''")).append("'");
            if (minPrice != null) sql.append(" AND price >= ").append(minPrice);
            if (maxPrice != null) sql.append(" AND price <= ").append(maxPrice);
            if ("price_asc".equals(sort)) sql.append(" ORDER BY price ASC");
            else if ("price_desc".equals(sort)) sql.append(" ORDER BY price DESC");
            else if ("newest".equals(sort)) sql.append(" ORDER BY create_time DESC");
            return jdbc.queryForList(sql.toString());
        } catch (Exception e) {
            log.warn("Mall DB query products failed: {}", e.getMessage());
            return List.of();
        }
    }

    public Optional<Map<String, Object>> findProductById(Long id) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM pms_product WHERE id = ?", id);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        } catch (Exception e) {
            log.warn("Mall DB query product by id failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<Map<String, Object>> findOrdersByMemberUsername(String username) {
        try {
            return jdbc.queryForList(
                    "SELECT o.* FROM oms_order o JOIN ums_member m ON o.member_id = m.id WHERE m.username = ? " +
                    "ORDER BY o.create_time DESC LIMIT 50", username);
        } catch (Exception e) {
            log.warn("Mall DB query orders by username failed: {}", e.getMessage());
            return List.of();
        }
    }

    public Optional<Map<String, Object>> findMemberByUsername(String username) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM ums_member WHERE username = ?", username);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        } catch (Exception e) {
            log.warn("Mall DB query member failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<Map<String, Object>> findOrdersByMemberId(Long memberId) {
        try {
            return jdbc.queryForList(
                    "SELECT * FROM oms_order WHERE member_id = ? ORDER BY create_time DESC LIMIT 50", memberId);
        } catch (Exception e) {
            log.warn("Mall DB query orders by member id failed: {}", e.getMessage());
            return List.of();
        }
    }

    public String generateOrderSn() {
        return "SN" + System.currentTimeMillis();
    }

    public long createOrder(Long memberId, String username, String orderSn, double total,
                            List<Map<String, Object>> items, String receiver, String phone, String address) {
        try {
            jdbc.update("INSERT INTO oms_order (member_id, order_sn, total_amount, pay_amount, receiver_name, " +
                    "receiver_phone, receiver_detail_address, source_type, status, order_type, create_time, delivery_company) " +
                    "VALUES (?,?,?,?,?,?,?,1,0,0,NOW(),'')",
                    memberId, orderSn, total, total, receiver, phone, address);
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT LAST_INSERT_ID() AS id");
            long orderId = ((Number) rows.get(0).get("id")).longValue();
            for (Map<String, Object> item : items) {
                jdbc.update("INSERT INTO oms_order_item (order_id, order_sn, product_id, product_name, product_price, " +
                        "product_quantity, product_pic) VALUES (?,?,?,?,?,?,?)",
                        orderId, orderSn,
                        item.getOrDefault("productId", 0),
                        item.getOrDefault("name", ""),
                        item.getOrDefault("price", 0),
                        item.getOrDefault("qty", 1),
                        item.getOrDefault("pic", ""));
            }
            log.info("Mall order created: orderSn={}, total={}", orderSn, total);
            return orderId;
        } catch (Exception e) {
            log.warn("Mall DB create order failed: {}", e.getMessage());
            throw new RuntimeException("创建订单失败", e);
        }
    }

    public Map<String, Object> createMember(String username, String phone) {
        try {
            jdbc.update("INSERT INTO ums_member (username, phone, nick_name, create_time, member_level_id, status) " +
                    "VALUES (?,?,?,NOW(),1,1)", username, phone, username);
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM ums_member WHERE username = ?", username);
            return rows.isEmpty() ? Map.of("id", 0, "username", username) : rows.get(0);
        } catch (Exception e) {
            log.warn("Mall DB create member failed: {}", e.getMessage());
            return Map.of("id", 0, "username", username);
        }
    }

    public List<Map<String, Object>> findAddressesByMemberId(Long memberId) {
        try {
            return jdbc.queryForList(
                    "SELECT * FROM ums_member_receive_address WHERE member_id = ?", memberId);
        } catch (Exception e) {
            log.warn("Mall DB query addresses failed: {}", e.getMessage());
            return List.of();
        }
    }

    public long addAddress(Long memberId, String name, String phone, String province,
                           String city, String region, String detail) {
        try {
            jdbc.update("INSERT INTO ums_member_receive_address (member_id, name, phone, province, city, region, " +
                    "detail_address, default_status, create_time) VALUES (?,?,?,?,?,?,?,0,NOW())",
                    memberId, name, phone, province, city, region, detail);
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT LAST_INSERT_ID() AS id");
            return ((Number) rows.get(0).get("id")).longValue();
        } catch (Exception e) {
            log.warn("Mall DB add address failed: {}", e.getMessage());
            throw new RuntimeException("添加地址失败", e);
        }
    }
}
