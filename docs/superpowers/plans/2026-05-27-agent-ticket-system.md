# 人工接管系统 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 AI 智能客服中增加完整的转人工接管系统：客服 Token 认证、工单 CRUD、自动分配和 Kafka 事件通知。

**Architecture:** 13 个新文件 + 1 个修改，完全复用现有模式（JPA + WebFlux + WebFilter + Kafka）。使用 UUID Token 认证（不引入 JWT 依赖），AgentAuthFilter 对标 ApiKeyAuthFilter。

**Tech Stack:** Java 17, Spring Boot 3 WebFlux, Spring Data JPA, H2/MySQL, Kafka, Flyway

---

### Task 1: Flyway 迁移 — 创建 agents + tickets 表

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__agent_ticket.sql`

- [ ] **Step 1: 写迁移 SQL**

```sql
CREATE TABLE agents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'AGENT',
    status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
    token VARCHAR(128),
    max_concurrent INT NOT NULL DEFAULT 5,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_event_id BIGINT,
    tenant_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    question TEXT,
    emotion_level VARCHAR(8) DEFAULT 'L0',
    topic VARCHAR(128),
    priority INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    assigned_agent_id BIGINT,
    ai_attempted_solutions TEXT,
    resolution TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ticket_status (status),
    INDEX idx_ticket_agent (assigned_agent_id),
    INDEX idx_ticket_tenant (tenant_id)
);

-- 插入一个默认管理员
INSERT INTO agents (username, password_hash, role, status)
VALUES ('admin', '$2a$10$dummy_hash_replace_in_code', 'ADMIN', 'ONLINE');
```

- [ ] **Step 2: 提交**

```bash
git add backend/src/main/resources/db/migration/V4__agent_ticket.sql
git commit -m "feat: add agents and tickets tables migration"
```

---

### Task 2: AgentEntity + AgentRepository

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/infrastructure/entity/AgentEntity.java`
- Create: `backend/src/main/java/com/cs/customerservice/infrastructure/repository/AgentRepository.java`

- [ ] **Step 1: 创建 AgentEntity**

```java
package com.cs.customerservice.infrastructure.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "agents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "OFFLINE";

    @Column(length = 128)
    private String token;

    @Column(name = "max_concurrent", nullable = false)
    @Builder.Default
    private Integer maxConcurrent = 5;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 2: 创建 AgentRepository**

```java
package com.cs.customerservice.infrastructure.repository;

import com.cs.customerservice.infrastructure.entity.AgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<AgentEntity, Long> {

    Optional<AgentEntity> findByUsername(String username);

    Optional<AgentEntity> findByToken(String token);

    List<AgentEntity> findByRoleAndStatus(String role, String status);
}
```

- [ ] **Step 3: 提交**

```bash
git add backend/src/main/java/com/cs/customerservice/infrastructure/entity/AgentEntity.java backend/src/main/java/com/cs/customerservice/infrastructure/repository/AgentRepository.java
git commit -m "feat: add AgentEntity and AgentRepository"
```

---

### Task 3: TicketEntity + TicketRepository

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/infrastructure/entity/TicketEntity.java`
- Create: `backend/src/main/java/com/cs/customerservice/infrastructure/repository/TicketRepository.java`

- [ ] **Step 1: 创建 TicketEntity**

```java
package com.cs.customerservice.infrastructure.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_event_id")
    private Long transferEventId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(name = "emotion_level", length = 8)
    @Builder.Default
    private String emotionLevel = "L0";

    @Column(length = 128)
    private String topic;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "assigned_agent_id")
    private Long assignedAgentId;

    @Column(name = "ai_attempted_solutions", columnDefinition = "TEXT")
    private String aiAttemptedSolutions;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
```

- [ ] **Step 2: 创建 TicketRepository**

```java
package com.cs.customerservice.infrastructure.repository;

import com.cs.customerservice.infrastructure.entity.TicketEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<TicketEntity, Long> {

    Page<TicketEntity> findByStatusAndTenantId(String status, String tenantId, Pageable pageable);

    Page<TicketEntity> findByAssignedAgentId(Long agentId, Pageable pageable);

    List<TicketEntity> findByStatusOrderByPriorityDescCreatedAtAsc(String status);

    @Query("SELECT t.status, COUNT(t) FROM TicketEntity t GROUP BY t.status")
    List<Object[]> countByStatus();

    long countByStatus(String status);

    long countByAssignedAgentIdAndStatusIn(Long agentId, List<String> statuses);
}
```

- [ ] **Step 3: 提交**

```bash
git add backend/src/main/java/com/cs/customerservice/infrastructure/entity/TicketEntity.java backend/src/main/java/com/cs/customerservice/infrastructure/repository/TicketRepository.java
git commit -m "feat: add TicketEntity and TicketRepository"
```

---

### Task 4: AgentTokenService — UUID Token 签发/验证

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/infrastructure/security/AgentTokenService.java`

- [ ] **Step 1: 创建 AgentTokenService**

```java
package com.cs.customerservice.infrastructure.security;

import com.cs.customerservice.infrastructure.entity.AgentEntity;
import com.cs.customerservice.infrastructure.repository.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.UUID;

@Service
public class AgentTokenService {

    private static final Logger log = LoggerFactory.getLogger(AgentTokenService.class);
    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AgentTokenService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public Mono<String> login(String username, String password) {
        return Mono.fromCallable(() -> {
            Optional<AgentEntity> opt = agentRepository.findByUsername(username);
            if (opt.isEmpty()) {
                throw new IllegalArgumentException("用户名或密码错误");
            }
            AgentEntity agent = opt.get();
            if (!passwordEncoder.matches(password, agent.getPasswordHash())) {
                throw new IllegalArgumentException("用户名或密码错误");
            }
            String token = UUID.randomUUID().toString();
            agent.setToken(token);
            agent.setStatus("ONLINE");
            agentRepository.save(agent);
            log.info("Agent login: username={}, role={}", username, agent.getRole());
            return token;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> logout(String token) {
        return Mono.fromRunnable(() -> {
            agentRepository.findByToken(token).ifPresent(agent -> {
                agent.setToken(null);
                agent.setStatus("OFFLINE");
                agentRepository.save(agent);
            });
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<AgentEntity> validate(String token) {
        return Mono.fromCallable(() ->
                agentRepository.findByToken(token).orElse(null)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/src/main/java/com/cs/customerservice/infrastructure/security/AgentTokenService.java
git commit -m "feat: add AgentTokenService for token-based auth"
```

---

### Task 5: AgentAuthFilter — WebFilter 认证

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/infrastructure/security/AgentAuthFilter.java`

- [ ] **Step 1: 创建 AgentAuthFilter**

```java
package com.cs.customerservice.infrastructure.security;

import com.cs.customerservice.infrastructure.entity.AgentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(2)
public class AgentAuthFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AgentAuthFilter.class);
    private static final String HEADER_NAME = "X-Agent-Token";

    private final AgentTokenService agentTokenService;

    public AgentAuthFilter(AgentTokenService agentTokenService) {
        this.agentTokenService = agentTokenService;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 只拦截 agent 和 ticket 路径
        if (!path.startsWith("/api/v1/agent/") && !path.startsWith("/api/v1/tickets")) {
            return chain.filter(exchange);
        }

        // login 和 register 放行
        if (path.equals("/api/v1/agent/login") || path.equals("/api/v1/agent/register")) {
            return chain.filter(exchange);
        }

        String token = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        if (token == null || token.isBlank()) {
            return unauthorized(exchange, "缺少 Agent Token，请在 X-Agent-Token 请求头中提供");
        }

        return agentTokenService.validate(token)
                .flatMap(agent -> {
                    if (agent == null) {
                        return unauthorized(exchange, "Agent Token 无效或已过期");
                    }
                    exchange.getAttributes().put("agent", agent);
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        log.warn("Agent auth failed: {}", msg);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(("{\"error\":\"" + msg + "\"}").getBytes())));
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/src/main/java/com/cs/customerservice/infrastructure/security/AgentAuthFilter.java
git commit -m "feat: add AgentAuthFilter for agent authentication"
```

---

### Task 6: DTOs

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/api/dto/AgentLoginRequest.java`
- Create: `backend/src/main/java/com/cs/customerservice/api/dto/AgentLoginResponse.java`
- Create: `backend/src/main/java/com/cs/customerservice/api/dto/TicketResponse.java`
- Create: `backend/src/main/java/com/cs/customerservice/api/dto/TicketUpdateRequest.java`
- Create: `backend/src/main/java/com/cs/customerservice/api/dto/TicketStatsResponse.java`

- [ ] **Step 1: 创建所有 DTO**

```java
// AgentLoginRequest.java
package com.cs.customerservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentLoginRequest {
    @NotBlank private String username;
    @NotBlank private String password;
}
```

```java
// AgentLoginResponse.java
package com.cs.customerservice.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLoginResponse {
    private String token;
    private String role;
    private String username;
}
```

```java
// TicketResponse.java
package com.cs.customerservice.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private Long id;
    private Long transferEventId;
    private String tenantId;
    private String sessionId;
    private String question;
    private String emotionLevel;
    private String topic;
    private Integer priority;
    private String status;
    private Long assignedAgentId;
    private String aiAttemptedSolutions;
    private String resolution;
    private Instant createdAt;
    private Instant updatedAt;
}
```

```java
// TicketUpdateRequest.java
package com.cs.customerservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketUpdateRequest {
    @NotBlank private String resolution;
}
```

```java
// TicketStatsResponse.java
package com.cs.customerservice.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatsResponse {
    private long pendingCount;
    private long assignedCount;
    private long inProgressCount;
    private long resolvedCount;
    private long totalCount;
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/src/main/java/com/cs/customerservice/api/dto/
git commit -m "feat: add agent and ticket DTOs"
```

---

### Task 7: AgentService — 客服登录/注册

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/application/agent/AgentService.java`

- [ ] **Step 1: 创建 AgentService**

```java
package com.cs.customerservice.application.agent;

import com.cs.customerservice.api.dto.AgentLoginRequest;
import com.cs.customerservice.api.dto.AgentLoginResponse;
import com.cs.customerservice.infrastructure.entity.AgentEntity;
import com.cs.customerservice.infrastructure.repository.AgentRepository;
import com.cs.customerservice.infrastructure.security.AgentTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private final AgentRepository agentRepository;
    private final AgentTokenService tokenService;

    public AgentService(AgentRepository agentRepository, AgentTokenService tokenService) {
        this.agentRepository = agentRepository;
        this.tokenService = tokenService;
    }

    public Mono<AgentLoginResponse> login(AgentLoginRequest request) {
        return tokenService.login(request.getUsername(), request.getPassword())
                .flatMap(token -> Mono.fromCallable(() ->
                        agentRepository.findByUsername(request.getUsername()).orElseThrow()
                ).subscribeOn(Schedulers.boundedElastic())
                        .map(agent -> AgentLoginResponse.builder()
                                .token(token)
                                .role(agent.getRole())
                                .username(agent.getUsername())
                                .build()));
    }

    public Mono<Void> logout(String token) {
        return tokenService.logout(token);
    }

    public Mono<AgentEntity> register(String username, String rawPassword, String role) {
        return Mono.fromCallable(() -> {
            if (agentRepository.findByUsername(username).isPresent()) {
                throw new IllegalArgumentException("用户名已存在");
            }
            AgentEntity agent = AgentEntity.builder()
                    .username(username)
                    .passwordHash(tokenService.encodePassword(rawPassword))
                    .role(role)
                    .status("OFFLINE")
                    .build();
            agentRepository.save(agent);
            log.info("Agent registered: username={}, role={}", username, role);
            return agent;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/src/main/java/com/cs/customerservice/application/agent/AgentService.java
git commit -m "feat: add AgentService with login/register"
```

---

### Task 8: TicketService — 工单 CRUD + 统计

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/application/ticket/TicketService.java`

- [ ] **Step 1: 创建 TicketService**

```java
package com.cs.customerservice.application.ticket;

import com.cs.customerservice.api.dto.TicketResponse;
import com.cs.customerservice.api.dto.TicketStatsResponse;
import com.cs.customerservice.api.dto.TicketUpdateRequest;
import com.cs.customerservice.infrastructure.entity.TicketEntity;
import com.cs.customerservice.infrastructure.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public Mono<Page<TicketResponse>> listByStatus(String status, String tenantId, Pageable pageable) {
        return Mono.fromCallable(() ->
                ticketRepository.findByStatusAndTenantId(status, tenantId, pageable)
                        .map(this::toDto)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Page<TicketResponse>> listByAgent(Long agentId, Pageable pageable) {
        return Mono.fromCallable(() ->
                ticketRepository.findByAssignedAgentId(agentId, pageable)
                        .map(this::toDto)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketResponse> findById(Long id) {
        return Mono.fromCallable(() ->
                ticketRepository.findById(id)
                        .map(this::toDto)
                        .orElse(null)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketResponse> claim(Long ticketId, Long agentId) {
        return Mono.fromCallable(() -> {
            TicketEntity ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
            if (!"PENDING".equals(ticket.getStatus())) {
                throw new IllegalStateException("工单状态不允许认领");
            }
            ticket.setStatus("ASSIGNED");
            ticket.setAssignedAgentId(agentId);
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Ticket claimed: id={}, agent={}", ticketId, agentId);
            return toDto(ticket);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketResponse> assign(Long ticketId, Long agentId) {
        return Mono.fromCallable(() -> {
            TicketEntity ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
            ticket.setStatus("ASSIGNED");
            ticket.setAssignedAgentId(agentId);
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Ticket assigned: id={}, agent={}", ticketId, agentId);
            return toDto(ticket);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketResponse> resolve(Long ticketId, Long agentId, TicketUpdateRequest request) {
        return Mono.fromCallable(() -> {
            TicketEntity ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
            if (!ticket.getAssignedAgentId().equals(agentId)) {
                throw new IllegalStateException("只能处理分配给自己的工单");
            }
            ticket.setStatus("RESOLVED");
            ticket.setResolution(request.getResolution());
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Ticket resolved: id={}, agent={}", ticketId, agentId);
            return toDto(ticket);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketResponse> close(Long ticketId, Long agentId, boolean isAdmin) {
        return Mono.fromCallable(() -> {
            TicketEntity ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
            if (!isAdmin && !agentId.equals(ticket.getAssignedAgentId())) {
                throw new IllegalStateException("只能关闭分配给自己的工单");
            }
            ticket.setStatus("CLOSED");
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Ticket closed: id={}, by agent={}", ticketId, agentId);
            return toDto(ticket);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketStatsResponse> stats() {
        return Mono.fromCallable(() -> {
            List<Object[]> rows = ticketRepository.countByStatus();
            long total = 0;
            long pending = 0, assigned = 0, inProgress = 0, resolved = 0;
            for (Object[] row : rows) {
                String status = (String) row[0];
                long count = (Long) row[1];
                total += count;
                switch (status) {
                    case "PENDING" -> pending = count;
                    case "ASSIGNED" -> assigned = count;
                    case "IN_PROGRESS" -> inProgress = count;
                    case "RESOLVED" -> resolved = count;
                }
            }
            return TicketStatsResponse.builder()
                    .pendingCount(pending).assignedCount(assigned)
                    .inProgressCount(inProgress).resolvedCount(resolved)
                    .totalCount(total).build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<TicketEntity> create(TicketEntity ticket) {
        return Mono.fromCallable(() -> ticketRepository.save(ticket))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private TicketResponse toDto(TicketEntity e) {
        return TicketResponse.builder()
                .id(e.getId()).transferEventId(e.getTransferEventId())
                .tenantId(e.getTenantId()).sessionId(e.getSessionId())
                .question(e.getQuestion()).emotionLevel(e.getEmotionLevel())
                .topic(e.getTopic()).priority(e.getPriority())
                .status(e.getStatus()).assignedAgentId(e.getAssignedAgentId())
                .aiAttemptedSolutions(e.getAiAttemptedSolutions())
                .resolution(e.getResolution())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/src/main/java/com/cs/customerservice/application/ticket/TicketService.java
git commit -m "feat: add TicketService with CRUD and stats"
```

---

### Task 9: TicketAssignmentService — 自动分配

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/application/ticket/TicketAssignmentService.java`

- [ ] **Step 1: 创建 TicketAssignmentService**

```java
package com.cs.customerservice.application.ticket;

import com.cs.customerservice.infrastructure.entity.AgentEntity;
import com.cs.customerservice.infrastructure.entity.TicketEntity;
import com.cs.customerservice.infrastructure.repository.AgentRepository;
import com.cs.customerservice.infrastructure.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

@Service
public class TicketAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(TicketAssignmentService.class);
    private final AgentRepository agentRepository;
    private final TicketRepository ticketRepository;

    public TicketAssignmentService(AgentRepository agentRepository, TicketRepository ticketRepository) {
        this.agentRepository = agentRepository;
        this.ticketRepository = ticketRepository;
    }

    public Mono<Void> autoAssign(TicketEntity ticket) {
        if (ticket.getPriority() < 3) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
            List<AgentEntity> onlineAgents = agentRepository
                    .findByRoleAndStatus("AGENT", "ONLINE");
            if (onlineAgents.isEmpty()) {
                log.warn("No online agent available for L3 ticket id={}", ticket.getId());
                return;
            }
            AgentEntity leastLoaded = onlineAgents.get(0);
            long minLoad = Long.MAX_VALUE;
            for (AgentEntity agent : onlineAgents) {
                long load = ticketRepository.countByAssignedAgentIdAndStatusIn(
                        agent.getId(), List.of("ASSIGNED", "IN_PROGRESS"));
                if (load < minLoad) {
                    minLoad = load;
                    leastLoaded = agent;
                }
            }
            ticket.setStatus("ASSIGNED");
            ticket.setAssignedAgentId(leastLoaded.getId());
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Auto-assigned L3 ticket id={} to agent={} (load={})",
                    ticket.getId(), leastLoaded.getUsername(), minLoad);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/src/main/java/com/cs/customerservice/application/ticket/TicketAssignmentService.java
git commit -m "feat: add TicketAssignmentService for auto-assignment"
```

---

### Task 10: AgentController — 客服认证 API

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/api/controller/AgentController.java`

- [ ] **Step 1: 创建 AgentController**

```java
package com.cs.customerservice.api.controller;

import com.cs.customerservice.api.dto.AgentLoginRequest;
import com.cs.customerservice.api.dto.AgentLoginResponse;
import com.cs.customerservice.application.agent.AgentService;
import com.cs.customerservice.infrastructure.entity.AgentEntity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/login")
    public Mono<AgentLoginResponse> login(@Valid @RequestBody AgentLoginRequest request) {
        log.info("Agent login: username={}", request.getUsername());
        return agentService.login(request)
                .onErrorResume(e -> Mono.error(new IllegalArgumentException(e.getMessage())));
    }

    @PostMapping("/logout")
    public Mono<Map<String, String>> logout(@RequestHeader("X-Agent-Token") String token) {
        return agentService.logout(token)
                .thenReturn(Map.of("message", "已退出登录"));
    }

    @PostMapping("/register")
    public Mono<Map<String, Object>> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(defaultValue = "AGENT") String role,
            ServerWebExchange exchange) {
        AgentEntity currentAgent = exchange.getAttribute("agent");
        if (currentAgent == null || !"ADMIN".equals(currentAgent.getRole())) {
            throw new IllegalStateException("仅管理员可注册新客服账号");
        }
        return agentService.register(username, password, role)
                .map(agent -> Map.<String, Object>of(
                        "id", agent.getId(),
                        "username", agent.getUsername(),
                        "role", agent.getRole()
                ));
    }

    @ExceptionHandler({IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleForbidden(Exception e) {
        return Map.of("error", e.getMessage());
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/src/main/java/com/cs/customerservice/api/controller/AgentController.java
git commit -m "feat: add AgentController for agent auth"
```

---

### Task 11: TicketController — 工单 API

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/api/controller/TicketController.java`

- [ ] **Step 1: 创建 TicketController**

```java
package com.cs.customerservice.api.controller;

import com.cs.customerservice.api.dto.TicketResponse;
import com.cs.customerservice.api.dto.TicketStatsResponse;
import com.cs.customerservice.api.dto.TicketUpdateRequest;
import com.cs.customerservice.application.ticket.TicketService;
import com.cs.customerservice.infrastructure.entity.AgentEntity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private static final Logger log = LoggerFactory.getLogger(TicketController.class);
    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    private AgentEntity getAgent(ServerWebExchange exchange) {
        return exchange.getAttribute("agent");
    }

    @GetMapping
    public Mono<Page<TicketResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tenantId,
            Pageable pageable,
            ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        if ("ADMIN".equals(agent.getRole()) || "TEAM_LEAD".equals(agent.getRole())) {
            String s = status != null ? status : "PENDING";
            String t = tenantId != null ? tenantId : "default";
            return ticketService.listByStatus(s, t, pageable);
        }
        return ticketService.listByAgent(agent.getId(), pageable);
    }

    @GetMapping("/{id}")
    public Mono<TicketResponse> get(@PathVariable Long id) {
        return ticketService.findById(id);
    }

    @PutMapping("/{id}/claim")
    public Mono<TicketResponse> claim(@PathVariable Long id, ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        return ticketService.claim(id, agent.getId());
    }

    @PutMapping("/{id}/assign")
    public Mono<TicketResponse> assign(@PathVariable Long id, @RequestParam Long agentId,
                                        ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        if (!"ADMIN".equals(agent.getRole()) && !"TEAM_LEAD".equals(agent.getRole())) {
            throw new IllegalStateException("无权限执行派发操作");
        }
        return ticketService.assign(id, agentId);
    }

    @PutMapping("/{id}/resolve")
    public Mono<TicketResponse> resolve(@PathVariable Long id,
                                         @Valid @RequestBody TicketUpdateRequest request,
                                         ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        return ticketService.resolve(id, agent.getId(), request);
    }

    @PutMapping("/{id}/close")
    public Mono<TicketResponse> close(@PathVariable Long id, ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        return ticketService.close(id, agent.getId(), "ADMIN".equals(agent.getRole()));
    }

    @GetMapping("/stats")
    public Mono<TicketStatsResponse> stats(ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        if (!"ADMIN".equals(agent.getRole()) && !"TEAM_LEAD".equals(agent.getRole())) {
            throw new IllegalStateException("无权限查看统计数据");
        }
        return ticketService.stats();
    }

    @ExceptionHandler({IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleForbidden(Exception e) {
        return Map.of("error", e.getMessage());
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/src/main/java/com/cs/customerservice/api/controller/TicketController.java
git commit -m "feat: add TicketController for ticket management"
```

---

### Task 12: Orchestrator 集成 — 转人工时自动创建工单

**Files:**
- Modify: `backend/src/main/java/com/cs/customerservice/application/orchestrator/CustomerChatOrchestrator.java`

- [ ] **Step 1: 注入 TicketService + TicketAssignmentService**

在 `CustomerChatOrchestrator` 中添加字段和构造参数：

```java
private final TicketService ticketService;
private final TicketAssignmentService ticketAssignmentService;
```

```java
// 构造方法参数尾部追加
TicketService ticketService,
TicketAssignmentService ticketAssignmentService

// 构造方法体内赋值
this.ticketService = ticketService;
this.ticketAssignmentService = ticketAssignmentService;
```

添加 import：
```java
import com.cs.customerservice.infrastructure.entity.TicketEntity;
import com.cs.customerservice.application.ticket.TicketService;
import com.cs.customerservice.application.ticket.TicketAssignmentService;
```

- [ ] **Step 2: 在 sendTransferEventAsync 中创建工单**

在 `sendTransferEventAsync` 方法末尾添加创建工单逻辑：

```java
private void sendTransferEventAsync(ChatRequest request, ChatResponse response, String question) {
    String emotionLevel = inferEmotionLevel(question);
    String topic = inferTopic(question);
    Map<String, String> event = Map.of(
            "sessionId", request.getSessionId(),
            "tenantId", request.getTenantId(),
            "userId", request.getUserId() != null ? request.getUserId() : "",
            "question", question,
            "emotionLevel", emotionLevel,
            "topic", topic,
            "attemptedSolutions", response.getAnswer()
    );
    transferEventProducer.send(request.getSessionId(), objectMapper.writeValueAsString(event))
            .subscribe();

    // 新增：自动创建工单
    int priority = emotionLevel != null && emotionLevel.startsWith("L") ? 
            Integer.parseInt(emotionLevel.substring(1)) : 0;
    TicketEntity ticket = TicketEntity.builder()
            .tenantId(request.getTenantId())
            .sessionId(request.getSessionId())
            .question(question)
            .emotionLevel(emotionLevel)
            .topic(topic)
            .priority(priority)
            .aiAttemptedSolutions(response.getAnswer())
            .status("PENDING")
            .build();
    ticketService.create(ticket)
            .flatMap(t -> ticketAssignmentService.autoAssign(t))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
}
```

注意：`transferEventProducer.send(...)` 返回 `Mono<Void>`，需要在它上面 wrap 异常处理（`log.warn(...)` + `subscribe()`），与已有模式的 `subscribe()` 一致。

- [ ] **Step 3: 提交**

```bash
git add backend/src/main/java/com/cs/customerservice/application/orchestrator/CustomerChatOrchestrator.java
git commit -m "feat: integrate ticket creation into orchestrator postProcess"
```

---

### Task 13: 编译验证

- [ ] **Step 1: 编译**

```bash
cd c:\Users\wenzhenkun\Desktop\xiangmu\backend
mvn compile
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: 如有编译错误，修正后重新编译**

- [ ] **Step 3: 提交（如有修复）**

```bash
git add -A && git commit -m "chore: fix compilation issues"
```
