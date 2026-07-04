package com.cs.customerservice.application.eval;

import com.cs.customerservice.application.service.KnowledgeRetrievalPort;
import com.cs.customerservice.domain.KnowledgeChunk;
import com.cs.customerservice.infrastructure.entity.ChatRecord;
import com.cs.customerservice.infrastructure.entity.ChatRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvalDatasetGenerator {

    private static final Logger log = LoggerFactory.getLogger(EvalDatasetGenerator.class);
    private static final int TOP_K = 5;
    private static final String ANSWER_SYSTEM_PROMPT =
            "你是一个客服专家。根据知识库内容，生成客服回答。回答要准确、完整、友好。";

    private final ChatRecordRepository chatRecordRepository;
    private final KnowledgeRetrievalPort knowledgeRetrievalPort;
    private final ChatClient answerClient;
    private final String evalYamlPath;

    public EvalDatasetGenerator(ChatRecordRepository chatRecordRepository,
                                KnowledgeRetrievalPort knowledgeRetrievalPort,
                                ChatClient.Builder chatClientBuilder,
                                @Value("${eval.testcases.path:src/main/resources/eval/eval-testcases.yaml}") String evalYamlPath) {
        this.chatRecordRepository = chatRecordRepository;
        this.knowledgeRetrievalPort = knowledgeRetrievalPort;
        this.answerClient = chatClientBuilder
                .defaultSystem(ANSWER_SYSTEM_PROMPT)
                .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .temperature(0.0)
                        .build())
                .build();
        this.evalYamlPath = evalYamlPath;
        log.info("Eval testcases YAML path: {}", evalYamlPath);
    }

    /**
     * 从最近对话中自动生成测试用例，追加到 YAML 文件。
     *
     * @param tenantId 租户
     * @param count    要生成的用例数
     * @return 实际生成的用例数
     */
    public Mono<Integer> generate(String tenantId, int count) {
        return Mono.fromCallable(() -> chatRecordRepository.findByTenantId(tenantId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .filter(r -> r.getQuestion() != null && !r.getQuestion().isBlank())
                .groupBy(ChatRecord::getSessionId)
                .flatMap(group -> group.next())  // 每 session 取第一条消息
                .distinct(ChatRecord::getQuestion)
                .take(count)
                .flatMap(record -> buildTestCase(record, tenantId)
                        .subscribeOn(Schedulers.boundedElastic()))
                .collectList()
                .flatMap(newCases -> {
                    if (newCases.isEmpty()) {
                        return Mono.just(0);
                    }
                    return appendToYaml(newCases);
                });
    }

    /** 根据一条对话记录构建测试用例 */
    private Mono<EvalTestCase> buildTestCase(ChatRecord record, String tenantId) {
        return knowledgeRetrievalPort.search(tenantId, record.getQuestion(), TOP_K)
                .flatMap(chunks -> {
                    List<String> chunkIds = chunks.stream()
                            .map(KnowledgeChunk::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    if (chunkIds.isEmpty()) {
                        log.warn("No chunks found for question: {}", record.getQuestion());
                        return Mono.just(new EvalTestCase(
                                nextId(), record.getQuestion(), List.of(), "", tenantId));
                    }

                    return generateExpectedAnswer(record.getQuestion(), chunks)
                            .map(answer -> new EvalTestCase(
                                    nextId(), record.getQuestion(), chunkIds, answer, tenantId));
                })
                .onErrorResume(e -> {
                    log.warn("Failed to build test case for: {}", record.getQuestion(), e);
                    return Mono.empty();
                });
    }

    /** LLM 根据知识库内容生成期望回答 */
    private Mono<String> generateExpectedAnswer(String question, List<KnowledgeChunk> chunks) {
        String context = chunks.stream()
                .map(KnowledgeChunk::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n---\n"));

        String prompt = String.format("""
                用户问题：%s

                知识库内容：
                %s

                请基于以上知识库内容，生成一个完整的客服回答。回答必须严格基于知识库，不要编造。
                """, question, context);

        return Mono.fromCallable(() ->
                answerClient.prompt().user(prompt).call().content())
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 追加新用例到 YAML 文件 */
    private Mono<Integer> appendToYaml(List<EvalTestCase> newCases) {
        return Mono.fromCallable(() -> {
            Yaml yaml = new Yaml();
            Path path = Paths.get(evalYamlPath);
            List<Map<String, Object>> existingCases = new ArrayList<>();

            if (Files.exists(path)) {
                try (InputStream is = Files.newInputStream(path)) {
                    Map<String, Object> root = yaml.load(is);
                    if (root != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> list = (List<Map<String, Object>>) root.get("testcases");
                        if (list != null) existingCases.addAll(list);
                    }
                }
            }

            // 去重：按 query 去重
            Set<String> existingQueries = existingCases.stream()
                    .map(m -> (String) m.get("query"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            int added = 0;
            for (EvalTestCase tc : newCases) {
                if (existingQueries.contains(tc.getQuery())) continue;

                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", tc.getId());
                map.put("query", tc.getQuery());
                map.put("expectedChunkIds", tc.getExpectedChunkIds() != null ? tc.getExpectedChunkIds() : List.of());
                map.put("expectedAnswer", tc.getExpectedAnswer() != null ? tc.getExpectedAnswer() : "");
                map.put("tenantId", tc.getTenantId());
                existingCases.add(map);
                existingQueries.add(tc.getQuery());
                added++;
            }

            if (added == 0) {
                log.info("No new test cases to add");
                return 0;
            }

            // 写回 YAML
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("testcases", existingCases);

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            Yaml outputYaml = new Yaml(options);

            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                writer.write("# RAG 评测测试用例\n");
                writer.write("# 自动生成 + 手动维护\n");
                writer.write("---\n");
                outputYaml.dump(root, writer);
            }

            log.info("Added {} test cases to {}", added, evalYamlPath);
            return added;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ========== ID 生成 ==========

    private int idCounter = 100;

    private String nextId() {
        return "tc-auto-" + (++idCounter);
    }
}
