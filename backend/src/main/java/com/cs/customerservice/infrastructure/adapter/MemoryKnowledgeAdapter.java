package com.cs.customerservice.infrastructure.adapter;

import com.cs.customerservice.application.service.KnowledgeRetrievalPort;
import com.cs.customerservice.domain.KnowledgeChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@ConditionalOnMissingBean(EsKnowledgeAdapter.class)
public class MemoryKnowledgeAdapter implements KnowledgeRetrievalPort {

    private static final Logger log = LoggerFactory.getLogger(MemoryKnowledgeAdapter.class);

    private static final Map<String, List<KnowledgeChunk>> FAQ_STORE = Map.of(
            "default", List.of(
                    KnowledgeChunk.builder().id("faq-1").tenantId("default").title("退货政策")
                            .content("7天内可无理由退货，15天内可换货。退货需保持商品完好，附购买凭证。").score(0.95).build(),
                    KnowledgeChunk.builder().id("faq-2").tenantId("default").title("物流查询")
                            .content("登录APP进入「我的订单」可查看物流轨迹，或联系在线客服提供订单号查询。").score(0.90).build(),
                    KnowledgeChunk.builder().id("faq-3").tenantId("default").title("优惠券使用")
                            .content("优惠券在结算页面选择使用，每笔订单限用一张，不可与其他活动叠加。").score(0.85).build(),
                    KnowledgeChunk.builder().id("faq-4").tenantId("default").title("会员权益")
                            .content("VIP会员享专属折扣、免运费、优先客服通道。月卡30元，年卡299元。").score(0.80).build()
            ),
            "tenant-a", List.of(
                    KnowledgeChunk.builder().id("faq-a1").tenantId("tenant-a").title("Tenant-A 专属退货")
                            .content("Tenant-A 用户享有14天无理由退货权益，含上门取件服务。").score(0.95).build()
            ),
            "tenant-b", List.of(
                    KnowledgeChunk.builder().id("faq-b1").tenantId("tenant-b").title("Tenant-B 专属客服")
                            .content("Tenant-B 提供7x24小时专属客服热线：400-xxx-xxxx。").score(0.95).build()
            )
    );

    @Override
    public Mono<List<KnowledgeChunk>> search(String tenantId, String query, int topK) {
        /*
         * 对接 Elasticsearch / VectorStore 说明：
         * 1. 将 FAQ 知识库向量化存入 ES dense_vector 字段或独立的 VectorStore（如 Pinecone/Milvus）
         * 2. 对 query 进行 embedding 编码后执行 kNN 检索
         * 3. 添加 tenantId 过滤：boolQuery.must(QueryBuilders.termQuery("tenantId", tenantId))
         * 4. 按 score 降序取 topK 条结果
         */
        return Mono.fromCallable(() -> {
            List<KnowledgeChunk> tenantFaqs = FAQ_STORE.getOrDefault(tenantId, FAQ_STORE.get("default"));

            return tenantFaqs.stream()
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(topK)
                    .peek(chunk -> log.debug("Retrieved FAQ: tenant={}, title={}, score={}",
                            chunk.getTenantId(), chunk.getTitle(), chunk.getScore()))
                    .collect(Collectors.toList());
        });
    }
}
