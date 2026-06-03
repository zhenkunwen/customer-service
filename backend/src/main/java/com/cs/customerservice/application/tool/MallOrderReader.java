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
}
