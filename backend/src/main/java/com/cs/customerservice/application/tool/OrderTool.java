package com.cs.customerservice.application.tool;

import com.cs.customerservice.infrastructure.entity.OrderEntity;
import com.cs.customerservice.infrastructure.repository.OrderRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class OrderTool implements Function<OrderTool.Request, OrderTool.Response> {

    private static final Logger log = LoggerFactory.getLogger(OrderTool.class);

    private final OrderRepository orderRepository;

    public OrderTool(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public record Request(
            @JsonProperty(required = true)
            @JsonPropertyDescription("用户ID") String userId,

            @JsonProperty(required = true)
            @JsonPropertyDescription("订单ID") String orderId
    ) {}

    public record Response(
            String orderId,
            String status,
            String amount,
            String createTime,
            String detail
    ) {}

    @Override
    public Response apply(Request request) {
        log.info("OrderTool called: userId={}, orderId={}", request.userId, request.orderId);

        return orderRepository.findByOrderId(request.orderId)
                .map(order -> {
                    log.debug("Order found: {} status={}", order.getOrderId(), order.getStatus());
                    return new Response(
                            order.getOrderId(),
                            order.getStatus(),
                            order.getAmount().toString(),
                            order.getCreateTime() != null ? order.getCreateTime().toString() : "",
                            order.getProductDetail() != null ? order.getProductDetail() : ""
                    );
                })
                .orElseGet(() -> {
                    log.warn("Order not found: orderId={}", request.orderId);
                    return new Response(request.orderId, "NOT_FOUND", "0", "", "未找到该订单，请核实订单号");
                });
    }
}
