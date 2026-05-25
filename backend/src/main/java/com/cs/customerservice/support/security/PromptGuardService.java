package com.cs.customerservice.support.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class PromptGuardService {

    private static final Logger log = LoggerFactory.getLogger(PromptGuardService.class);

    private static final Set<String> GLOBAL_BLACKLIST = Set.of(
            "忽略.*指令", "ignore.*instruction", "system.*prompt",
            "DAN", "jailbreak", "越狱",
            "忘记.*规则", "forget.*rules",
            "你是一台", "you are a",
            "输出原始", "output raw",
            "<script", "javascript:",
            "DROP TABLE", "DROP DATABASE",
            "UNION SELECT", "' OR '1'='1",
            "\\.\\./", "\\.\\.\\\\",
            "\\\\x", "\\\\u00",
            "\\$\\{", "#\\{",
            "rm -rf", "cat /etc/passwd"
    );

    private static final List<Pattern> COMPILED_PATTERNS = GLOBAL_BLACKLIST.stream()
            .map(s -> Pattern.compile(s, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .toList();

    private final Map<String, Set<String>> tenantCustomWords = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        tenantCustomWords.put("tenant-a", Set.of("竞品词A", "competitorA"));
        tenantCustomWords.put("tenant-b", Set.of("竞品词B"));
        log.info("PromptGuardService initialized with {} global patterns, {} tenant word-sets",
                COMPILED_PATTERNS.size(), tenantCustomWords.size());
    }

    public String sanitize(String question, String tenantId) {
        if (question == null || question.isBlank()) {
            return question;
        }

        for (Pattern pattern : COMPILED_PATTERNS) {
            if (pattern.matcher(question).find()) {
                log.warn("Blocked prompt for tenant={}: pattern={} matched", tenantId, pattern.pattern());
                throw new PromptGuardException("输入包含不安全内容，请修改后重试");
            }
        }

        Set<String> customWords = tenantCustomWords.getOrDefault(tenantId, Set.of());
        for (String word : customWords) {
            if (question.toLowerCase().contains(word.toLowerCase())) {
                log.warn("Blocked prompt for tenant={}: custom word matched", tenantId);
                throw new PromptGuardException("输入包含敏感内容，请修改后重试");
            }
        }

        return question.trim();
    }

    public static class PromptGuardException extends RuntimeException {
        public PromptGuardException(String message) {
            super(message);
        }
    }
}
