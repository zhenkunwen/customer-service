package com.cs.customerservice.application.tool;

import com.cs.customerservice.infrastructure.entity.OrderEntity;
import com.cs.customerservice.infrastructure.repository.OrderRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 订单查询工具类
 * <p>
 * 这是一个Spring组件，实现了Function接口，允许以函数式编程风格进行订单查询。
 * 通常用于AI工具调用或服务编排场景，接收订单查询请求并返回订单详情。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * OrderTool tool = new OrderTool(orderRepository);
 * OrderTool.Request request = new OrderTool.Request("user123", "order456");
 * OrderTool.Response response = tool.apply(request);
 * </pre>
 * 
 * @author Your Name
 * @version 1.0
 */
@Component  // 标记为Spring组件，让Spring容器自动扫描并管理该类的实例
public class OrderTool implements Function<OrderTool.Request, OrderTool.Response> {

    // 日志记录器，用于记录工具调用过程中的信息、警告和错误
    private static final Logger log = LoggerFactory.getLogger(OrderTool.class);

    // 订单数据访问层（Repository），用于执行数据库操作（查询订单）
    private final OrderRepository orderRepository;
    private final MallOrderReader mallOrderReader;

    /**
     * 构造方法，通过依赖注入获取OrderRepository实例
     * 
     * @param orderRepository 订单数据仓库，用于访问订单数据
     */
    public OrderTool(OrderRepository orderRepository, MallOrderReader mallOrderReader) {
        this.orderRepository = orderRepository;
        this.mallOrderReader = mallOrderReader;
    }

    /**
     * 订单查询请求参数记录（Record）
     * <p>
     * Record是Java 16引入的不可变数据载体，自动生成构造方法、equals、hashCode和toString。
     * 这里定义了两个必需的查询参数，并带有JSON属性描述，便于序列化/反序列化（例如用于AI工具调用）
     * </p>
     */
    public record Request(
            /**
             * 用户ID
             * required = true 表示该字段在JSON中必须存在
             * description 提供了字段的说明，可用于生成API文档或AI提示
             */
            @JsonProperty(required = true)
            @JsonPropertyDescription("用户ID") 
            String userId,

            /**
             * 订单ID
             * 必须提供，用于唯一标识要查询的订单
             */
            @JsonProperty(required = true)
            @JsonPropertyDescription("订单ID") 
            String orderId
    ) {}

    /**
     * 订单查询响应结果记录（Record）
     * 包含了订单的主要信息，如果订单不存在则返回特殊状态和提示信息
     */
    public record Response(
            String orderId,      // 订单编号
            String status,       // 订单状态（例如：PAID, SHIPPED, NOT_FOUND）
            String amount,       // 订单金额（以字符串形式返回，避免浮点数精度问题）
            String createTime,   // 订单创建时间（格式取决于数据库存储，通常为ISO字符串）
            String detail        // 订单详情，如商品描述；如果订单不存在则包含错误提示信息
    ) {}

    /**
     * 应用函数，执行订单查询逻辑
     * <p>
     * 这是Function接口的抽象方法，接收Request对象，返回Response对象。
     * 方法内部通过订单ID从数据库查询订单，如果找到则映射为Response对象；
     * 如果未找到，则返回一个带有"NOT_FOUND"状态和提示信息的Response。
     * </p>
     * 
     * @param request 包含userId和orderId的请求对象（实际查询中userId可能用于权限校验，这里仅记录日志）
     * @return 包含订单信息的Response对象，永远不为null
     */
    @Override
    public Response apply(Request request) {
        log.info("OrderTool called: userId={}, orderId={}", request.userId, request.orderId);
        var mallOrder = mallOrderReader.findOrderBySn(request.orderId);
        if (mallOrder.isPresent()) {
            log.debug("Order found in mall: {}", request.orderId);
            var o = mallOrder.get();
            Object status = o.get("status");
            String statusStr = status != null ? status.toString() : "UNKNOWN";
            Object amount = o.get("total_amount");
            String amountStr = amount != null ? amount.toString() : "0";
            Object createTime = o.get("create_time");
            String timeStr = createTime != null ? createTime.toString() : "";
            String detail = o.get("order_sn") + " | " + (o.get("member_username") != null ? o.get("member_username") : "");
            return new Response(request.orderId, statusStr, amountStr, timeStr, detail);
        }
        return orderRepository.findByOrderId(request.orderId)
                .map(order -> {
                    log.debug("Order found: {} status={}", order.getOrderId(), order.getStatus());
                    return new Response(
                            order.getOrderId(),
                            order.getStatus(),
                            order.getAmount() != null ? order.getAmount().toString() : "0",
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