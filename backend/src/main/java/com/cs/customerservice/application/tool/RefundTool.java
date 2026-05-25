package com.cs.customerservice.application.tool;

import com.cs.customerservice.infrastructure.entity.RefundPolicyEntity;
import com.cs.customerservice.infrastructure.repository.RefundPolicyRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class RefundTool implements Function<RefundTool.Request, RefundTool.Response> {

    private static final Logger log = LoggerFactory.getLogger(RefundTool.class);

    private final RefundPolicyRepository refundPolicyRepository;

    public RefundTool(RefundPolicyRepository refundPolicyRepository) {
        this.refundPolicyRepository = refundPolicyRepository;
    }

    public record Request(
            @JsonProperty(required = true)
            @JsonPropertyDescription("商品类型，如：电子产品、服饰、食品、日用品") String productType
    ) {}

    public record Response(
            String productType,
            int refundDays,
            String condition,
            String policyDetail
    ) {}

    @Override
    public Response apply(Request request) {
        log.info("RefundTool called: productType={}", request.productType);

        return refundPolicyRepository.findByProductType(request.productType)
                .map(policy -> new Response(
                        policy.getProductType(),
                        policy.getRefundDays(),
                        policy.getReturnConditions(),
                        policy.getPolicyDetail()
                ))
                .orElseGet(() -> {
                    // Fallback to the generic policy
                    return refundPolicyRepository.findByProductType("通用")
                            .map(policy -> new Response(
                                    request.productType,
                                    policy.getRefundDays(),
                                    policy.getReturnConditions(),
                                    policy.getPolicyDetail()
                            ))
                            .orElse(new Response(request.productType, 7, "商品完好、附购买凭证",
                                    "自签收之日起7天内可申请无理由退货。特殊商品（定制类、虚拟商品等）不支持退货，详见商品详情页。"));
                });
    }
}
