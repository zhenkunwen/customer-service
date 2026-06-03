package com.cs.customerservice.application.tool;

import com.cs.customerservice.infrastructure.entity.RefundPolicyEntity;
import com.cs.customerservice.infrastructure.repository.RefundPolicyRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 退货退款政策查询工具类
 * <p>
 * 这是一个Spring组件，实现了Function接口，用于根据商品类型查询对应的退货退款政策。
 * 通常用于AI客服、购物车结算提示、售后政策咨询等场景，接收商品类型并返回可退货天数、退货条件及政策详情。
 * 如果指定商品类型的政策不存在，则自动降级查询“通用”政策；若通用政策也不存在，则返回一套硬编码的默认政策。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * RefundTool tool = new RefundTool(refundPolicyRepository);
 * RefundTool.Request request = new RefundTool.Request("电子产品");
 * RefundTool.Response response = tool.apply(request);
 * System.out.println("可退货天数：" + response.refundDays());
 * </pre>
 * 
 * @author Your Name
 * @version 1.0
 */
@Component  // 标记为Spring组件，让Spring容器自动扫描并管理该类的实例
public class RefundTool implements Function<RefundTool.Request, RefundTool.Response> {

    // 日志记录器，用于记录查询请求、降级处理及异常情况
    private static final Logger log = LoggerFactory.getLogger(RefundTool.class);

    // 退货政策数据访问层，用于从数据库查询不同商品类型的退货策略
    private final RefundPolicyRepository refundPolicyRepository;
    private final MallOrderReader mallOrderReader;

    /**
     * 构造方法，通过依赖注入获取 RefundPolicyRepository 实例
     * 
     * @param refundPolicyRepository 退货政策数据仓库
     */
    public RefundTool(RefundPolicyRepository refundPolicyRepository, MallOrderReader mallOrderReader) {
        this.refundPolicyRepository = refundPolicyRepository;
        this.mallOrderReader = mallOrderReader;
    }

    /**
     * 退货政策查询请求参数记录
     * <p>
     * 包含一个必需的商品类型字段，用于确定使用哪种退货政策。
     * JSON属性注解便于序列化/反序列化，在AI工具调用场景中自动生成请求参数结构。
     * </p>
     */
    public record Request(
            /**
             * 商品类型（如：电子产品、服饰、食品、日用品等）
             * required = true 表示该字段在请求JSON中必须提供
             * description 提供字段示例说明，用于文档或AI提示
             */
            @JsonProperty(required = true)
            @JsonPropertyDescription("商品类型，如：电子产品、服饰、食品、日用品")
            String productType
    ) {}

    /**
     * 退货政策查询响应结果记录
     * <p>
     * 返回指定商品类型的退货政策详情，包含可退货天数、退货条件说明以及详细政策文本。
     * 如果使用了降级政策，productType 字段仍保留原始请求的商品类型（而非“通用”），便于调用方识别。
     * </p>
     */
    public record Response(
            String productType,   // 商品类型（原始请求中的类型，即使使用的是通用政策也保留原始值）
            int refundDays,       // 签收后可申请退货的天数（例如7天、15天）
            String condition,     // 退货条件简述，如“商品完好、附购买凭证”
            String policyDetail   // 政策详细说明，可能包含特殊商品除外条款
    ) {}

    /**
     * 应用函数，根据商品类型查询退货政策
     * <p>
     * 执行逻辑如下：
     * <ol>
     *   <li>记录调用日志，包含请求的商品类型</li>
     *   <li>根据商品类型从数据库查询对应的退款政策（Optional）</li>
     *   <li>如果找到了精确匹配的政策，则直接转换为Response对象返回</li>
     *   <li>如果没有找到，则降级查询“通用”政策（表示所有商品通用的基础政策）</li>
     *   <li>如果通用政策也不存在，则使用硬编码的默认政策（7天无理由退货，含特殊商品说明）</li>
     *   <li>最终返回的Response中，productType字段始终为原始请求的商品类型，而不是“通用”，以便前端显示</li>
     * </ol>
     * 这种降级设计保证了即使数据库配置不完整，系统也能返回一个合理的政策说明，提升用户体验。
     * </p>
     * 
     * @param request 包含商品类型的请求对象
     * @return 包含退货政策的Response对象，保证不为null
     */
    @Override
    public Response apply(Request request) {
        // 记录查询请求日志，便于后续审计和问题定位
        log.info("RefundTool called: productType={}", request.productType);

        var reasons = mallOrderReader.findReturnReasons();
        if (!reasons.isEmpty()) {
            var r = reasons.get(0);
            return new Response(request.productType, 7, r.get("name").toString(), r.get("name").toString());
        }
        // 第一步：精确匹配商品类型的政策
        return refundPolicyRepository.findByProductType(request.productType)
                // 如果精确匹配成功，直接将实体转换为Response
                .map(policy -> new Response(
                        policy.getProductType(),        // 商品类型（数据库中的类型，等于请求类型）
                        policy.getRefundDays(),         // 可退货天数
                        policy.getReturnConditions(),   // 退货条件
                        policy.getPolicyDetail()        // 详细政策说明
                ))
                // 第二步：精确匹配失败，尝试降级使用通用政策
                .orElseGet(() -> {
                    log.debug("No specific policy for '{}', falling back to generic policy", request.productType);
                    
                    // 查询通用政策（假设数据库中productType='通用'的记录代表默认策略）
                    return refundPolicyRepository.findByProductType("通用")
                            .map(policy -> new Response(
                                    request.productType,                    // 注意：这里仍保留原始商品类型，而不是“通用”
                                    policy.getRefundDays(),                 // 使用通用政策的退货天数
                                    policy.getReturnConditions(),           // 使用通用政策的退货条件
                                    policy.getPolicyDetail()                // 使用通用政策的详细说明
                            ))
                            // 第三步：连通用政策都没有配置，则返回硬编码的默认政策（防御性编程）
                            .orElseGet(() -> {
                                log.warn("No generic policy found in database, using hardcoded default policy");
                                return new Response(
                                        request.productType,          // 原始商品类型
                                        7,                            // 默认7天退货
                                        "商品完好、附购买凭证",         // 默认条件
                                        "自签收之日起7天内可申请无理由退货。特殊商品（定制类、虚拟商品等）不支持退货，详见商品详情页。"
                                );
                            });
                });
    }
}