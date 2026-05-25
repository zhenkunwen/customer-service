package com.cs.customerservice.application.service;

import com.cs.customerservice.infrastructure.repository.TransferEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 未解决问题聚类与知识库补全建议。
 * 定时统计高频转人工话题，为知识库更新提供数据支撑。
 */
@Service
public class UnresolvedTopicService {

    private static final Logger log = LoggerFactory.getLogger(UnresolvedTopicService.class);

    private final TransferEventRepository transferEventRepository;

    public UnresolvedTopicService(TransferEventRepository transferEventRepository) {
        this.transferEventRepository = transferEventRepository;
    }

    /**
     * 每日凌晨统计近7天的高频转人工话题
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void clusterUnresolvedTopics() {
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Object[]> topicCounts = transferEventRepository.countByTopicSince(since);

        if (topicCounts.isEmpty()) {
            log.info("No transfer events in the last 7 days, skipping cluster");
            return;
        }

        log.info("=== 近7天转人工话题统计 ===");
        for (Object[] row : topicCounts) {
            String topic = (String) row[0];
            long count = (Long) row[1];
            log.info("话题[{}]: {} 次转人工", topic, count);
        }

        // 高频话题提醒知识库补全
        topicCounts.stream()
                .filter(row -> (Long) row[1] >= 5)
                .forEach(row -> {
                    String topic = (String) row[0];
                    long count = (Long) row[1];
                    log.warn("【知识库补全建议】话题[{}] 转人工次数达 {}次，建议相关团队更新知识库", topic, count);
                });
    }

    /**
     * 获取待人工解决的未关闭转人工事件
     */
    public long countPendingResolutions() {
        return transferEventRepository.findByResolvedFalse().size();
    }
}
