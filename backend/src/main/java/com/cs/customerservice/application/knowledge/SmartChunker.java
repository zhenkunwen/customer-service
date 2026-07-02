package com.cs.customerservice.application.knowledge;

import com.cs.customerservice.domain.KnowledgeChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SmartChunker {

    private static final Logger log = LoggerFactory.getLogger(SmartChunker.class);

    // 语义边界模式（按优先级排列）
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "(?m)^[#]{1,6}\\s+.*$"                                    // Markdown: # ##
            + "|^(?:第[一二三四五六七八九十百千]+[章节条篇]\\s*.*)$"    // 第X章/节/条/篇
            + "|^(?:[一二三四五六七八九十]+[、]\\s*.*)$"                // 一、二、三、
            + "|^(?:（[一二三四五六七八九十]+）\\s*.*)$"               // （一）（二）
            + "|^(?:\\d+\\.\\s*.*)$"                                   // 1. 2. 3.
    );

    private final ChunkConfig chunkConfig;

    public SmartChunker(ChunkConfig chunkConfig) {
        this.chunkConfig = chunkConfig;
    }

    /**
     * 三段式切分：语义边界 → 长度归一 → 重叠+元数据
     * @param text      原始文本
     * @param source    来源文件名
     * @param docType   文档类型（tech/narrative/policy）
     * @param totalPages PDF 总页码（非 PDF 传 0）
     * @return 知识块列表
     */
    public List<KnowledgeChunk> chunk(String text, String source, String docType, int totalPages) {
        ChunkConfig.Strategy strategy = chunkConfig.getStrategy(docType);

        // Stage 1: 语义边界切分
        List<SectionBlock> sections = splitBySemanticBoundaries(text);

        // Stage 2: 长度归一化（合并过小、切分过大）
        List<SectionBlock> normalized = normalizeLength(sections, strategy);

        // Stage 3: 重叠 + 元数据
        return addOverlapAndMetadata(normalized, strategy, source, docType, totalPages);
    }

    // ========== Stage 1: 语义边界切分 ==========

    static List<SectionBlock> splitBySemanticBoundaries(String text) {
        List<SectionBlock> blocks = new ArrayList<>();
        List<SectionBoundary> boundaries = new ArrayList<>();

        // 找出所有标题/章节边界
        Matcher m = SECTION_PATTERN.matcher(text);
        while (m.find()) {
            boundaries.add(new SectionBoundary(m.start(), m.end(), m.group().trim()));
        }

        if (boundaries.isEmpty()) {
            // 没有找到章节结构，按双换行切
            String[] parts = text.split("\\n\\s*\\n");
            for (int i = 0; i < parts.length; i++) {
                blocks.add(new SectionBlock(parts[i].trim(), "段落" + (i + 1)));
            }
            return blocks;
        }

        // 按边界切分
        for (int i = 0; i < boundaries.size(); i++) {
            int start = boundaries.get(i).endPos;
            int end = (i + 1 < boundaries.size()) ? boundaries.get(i + 1).startPos : text.length();
            String sectionTitle = boundaries.get(i).title;
            String content = text.substring(start, end).trim();
            if (!content.isEmpty()) {
                blocks.add(new SectionBlock(content, sectionTitle));
            }
        }

        return blocks;
    }

    // ========== Stage 2: 长度归一化 ==========

    static List<SectionBlock> normalizeLength(List<SectionBlock> sections, ChunkConfig.Strategy strategy) {
        List<SectionBlock> result = new ArrayList<>();
        SectionBlock pending = null;

        for (SectionBlock block : sections) {
            if (block.content.length() < strategy.minSize()) {
                // 太小：尝试合并到 pending
                if (pending == null) {
                    pending = block;
                } else if (pending.content.length() + block.content.length() <= strategy.maxSize()) {
                    pending = new SectionBlock(
                            pending.content + "\n" + block.content,
                            pending.sectionTitle
                    );
                } else {
                    result.add(pending);
                    pending = block;
                }
            } else if (block.content.length() <= strategy.maxSize()) {
                // 正好：直接产出
                if (pending != null) {
                    result.add(pending);
                    pending = null;
                }
                result.add(block);
            } else {
                // 太大：在句子边界切分
                if (pending != null) {
                    result.add(pending);
                    pending = null;
                }
                result.addAll(splitOversize(block, strategy.maxSize()));
            }
        }
        if (pending != null) {
            result.add(pending);
        }

        return result;
    }

    static List<SectionBlock> splitOversize(SectionBlock block, int maxSize) {
        List<SectionBlock> parts = new ArrayList<>();
        String remaining = block.content;
        while (remaining.length() > maxSize) {
            int breakAt = findBreakPoint(remaining, maxSize);
            parts.add(new SectionBlock(remaining.substring(0, breakAt).trim(), block.sectionTitle));
            remaining = remaining.substring(breakAt).trim();
        }
        if (!remaining.isEmpty()) {
            parts.add(new SectionBlock(remaining, block.sectionTitle));
        }
        return parts;
    }

    static int findBreakPoint(String text, int around) {
        for (int i = around; i > Math.max(around - 100, 0); i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '\n' || c == '！' || c == '？' || c == '；') {
                return i + 1;
            }
        }
        for (int i = around; i < Math.min(around + 50, text.length()); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '\n' || c == '！' || c == '？') {
                return i + 1;
            }
        }
        return around; // 找不到就硬切
    }

    // ========== Stage 3: 重叠 + 元数据 ==========

    List<KnowledgeChunk> addOverlapAndMetadata(
            List<SectionBlock> blocks, ChunkConfig.Strategy strategy,
            String source, String docType, int totalPages) {

        List<KnowledgeChunk> result = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++) {
            String content = blocks.get(i).content;

            // 不是第一块：加上上一块尾部 overlap 字作为上下文
            if (i > 0) {
                String prevTail = blocks.get(i - 1).content;
                String overlap = prevTail.substring(
                        Math.max(0, prevTail.length() - strategy.overlap()));
                content = overlap + "\n" + content;
            }

            String id = UUID.randomUUID().toString();
            result.add(KnowledgeChunk.builder()
                    .id(id)
                    .tenantId("default")
                    .title(blocks.get(i).sectionTitle)
                    .content(content)
                    .score(0.9)
                    .source(source)
                    .section(blocks.get(i).sectionTitle)
                    .chunkIndex(i)
                    .totalChunks(blocks.size())
                    .pageNum(null) // 精确页码需 PDF 坐标计算，暂缺
                    .docType(docType)
                    .build());
        }

        return result;
    }

    // ========== 内部数据结构 ==========

    record SectionBlock(String content, String sectionTitle) {}
    record SectionBoundary(int startPos, int endPos, String title) {}
}
