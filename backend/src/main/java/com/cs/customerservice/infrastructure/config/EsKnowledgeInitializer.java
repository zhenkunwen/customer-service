package com.cs.customerservice.infrastructure.config;

import com.cs.customerservice.infrastructure.adapter.EsKnowledgeAdapter;
import com.cs.customerservice.domain.KnowledgeChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@ConditionalOnBean(EsKnowledgeAdapter.class)
public class EsKnowledgeInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EsKnowledgeInitializer.class);

    private final EsKnowledgeAdapter esAdapter;

    public EsKnowledgeInitializer(EsKnowledgeAdapter esAdapter) {
        this.esAdapter = esAdapter;
    }

    @Override
    public void run(String... args) {
        esAdapter.listByTenant("default")
                .filter(List::isEmpty)
                .flatMap(ignored -> {
                    log.info("ES knowledge base empty, seeding initial FAQ data...");
                    return migrateAll(seedData());
                })
                .block();
    }

    private List<KnowledgeChunk> seedData() {
        return List.of(
                KnowledgeChunk.builder().id("faq-1").tenantId("default").title("退货政策")
                        .content("7天内可无理由退货，15天内可换货。退货需保持商品完好，附购买凭证。").score(0.95).build(),
                KnowledgeChunk.builder().id("faq-2").tenantId("default").title("物流查询")
                        .content("登录APP进入「我的订单」可查看物流轨迹，或联系在线客服提供订单号查询。").score(0.90).build(),
                KnowledgeChunk.builder().id("faq-3").tenantId("default").title("优惠券使用")
                        .content("优惠券在结算页面选择使用，每笔订单限用一张，不可与其他活动叠加。").score(0.85).build(),
                KnowledgeChunk.builder().id("faq-4").tenantId("default").title("会员权益")
                        .content("VIP会员享专属折扣、免运费、优先客服通道。月卡30元，年卡299元。").score(0.80).build(),
                KnowledgeChunk.builder().id("faq-a1").tenantId("tenant-a").title("Tenant-A 专属退货")
                        .content("Tenant-A 用户享有14天无理由退货权益，含上门取件服务。").score(0.95).build(),
                KnowledgeChunk.builder().id("faq-b1").tenantId("tenant-b").title("Tenant-B 专属客服")
                        .content("Tenant-B 提供7x24小时专属客服热线：400-xxx-xxxx。").score(0.95).build()
        );
    }

    private Mono<Void> migrateAll(List<KnowledgeChunk> chunks) {
        List<Mono<Void>> saves = chunks.stream()
                .map(chunk -> esAdapter.save(chunk))
                .toList();
        return Mono.when(saves)
                .doOnSuccess(v -> log.info("Seeded {} knowledge entries to ES", chunks.size()));
    }
}
