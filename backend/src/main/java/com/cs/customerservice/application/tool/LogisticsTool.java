package com.cs.customerservice.application.tool;

import com.cs.customerservice.infrastructure.entity.LogisticsTraceEntity;
import com.cs.customerservice.infrastructure.entity.LogisticsTraceNodeEntity;
import com.cs.customerservice.infrastructure.repository.LogisticsTraceRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LogisticsTool implements Function<LogisticsTool.Request, LogisticsTool.Response> {

    private static final Logger log = LoggerFactory.getLogger(LogisticsTool.class);

    private final LogisticsTraceRepository logisticsTraceRepository;
    private final MallOrderReader mallOrderReader;

    public LogisticsTool(LogisticsTraceRepository logisticsTraceRepository, MallOrderReader mallOrderReader) {
        this.logisticsTraceRepository = logisticsTraceRepository;
        this.mallOrderReader = mallOrderReader;
    }

    public record Request(
            @JsonProperty(required = true)
            @JsonPropertyDescription("订单ID") String orderId
    ) {}

    public record Response(
            String orderId,
            String carrier,
            String trackingNo,
            String currentStatus,
            List<TraceNode> traces
    ) {}

    public record TraceNode(
            String time,
            String status,
            String location
    ) {}

    @Override
    public Response apply(Request request) {
        log.info("LogisticsTool called: orderId={}", request.orderId);
        var mallOrder = mallOrderReader.findOrderBySn(request.orderId);
        if (mallOrder.isPresent()) {
            log.debug("Logistics found in mall: {}", request.orderId);
            var o = mallOrder.get();
            Object idObj = o.get("id");
            if (idObj != null) {
                Long mallOrderId = idObj instanceof Number ? ((Number) idObj).longValue() : null;
                var history = mallOrderReader.findOrderHistory(mallOrderId);
                if (!history.isEmpty()) {
                    var latest = history.get(0);
                    String carrier = o.get("delivery_company") != null ? o.get("delivery_company").toString() : "未知";
                    String trackingNo = o.get("delivery_sn") != null ? o.get("delivery_sn").toString() : "无";
                    String status = o.get("status") != null ? o.get("status").toString() : "暂无物流信息";
                    List<TraceNode> nodes = history.stream().map(h -> {
                        String t = h.get("create_time") != null ? h.get("create_time").toString() : "";
                        String s = h.get("note") != null ? h.get("note").toString() : "";
                        return new TraceNode(t, s, "");
                    }).collect(Collectors.toList());
                    return new Response(request.orderId, carrier, trackingNo, status, nodes);
                }
            }
            return new Response(request.orderId, "未知", "无", "暂无物流信息", List.of());
        }
        return logisticsTraceRepository.findByOrderId(request.orderId)
                .map(trace -> {
                    List<TraceNode> nodes = trace.getNodes().stream()
                            .map(n -> new TraceNode(
                                    n.getEventTime() != null ? n.getEventTime().toString() : "",
                                    n.getStatusDesc(),
                                    n.getLocation()
                            ))
                            .collect(Collectors.toList());
                    return new Response(
                            trace.getOrderId(),
                            trace.getCarrier(),
                            trace.getTrackingNo(),
                            trace.getCurrentStatus(),
                            nodes
                    );
                })
                .orElseGet(() -> {
                    log.warn("Logistics not found for orderId={}", request.orderId);
                    return new Response(request.orderId, "未知", "无", "暂无物流信息", List.of());
                });
    }
}
