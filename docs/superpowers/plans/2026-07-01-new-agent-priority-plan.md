# 新注册客服优先派单 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修改自动派单策略，按注册时间倒序优先派单给新客服，并用 `maxConcurrent` 防止过载

**Architecture:** 只改后端 Java 代码，不改前端、数据模型和 API。`TicketAssignmentService` 替换轮询为注册时间排序 + 负载感知，`TicketRepository` 新增一个批量查询方法。

**Tech Stack:** Java 17 + Spring Boot 3 WebFlux + Spring Data JPA

---

### Task 1: 新增 TicketRepository 批量查询方法

**Files:**
- Modify: `backend/src/main/java/com/cs/customerservice/infrastructure/repository/TicketRepository.java`

- [ ] **Step 1: 添加批量查询方法**

在 `TicketRepository` 中添加一个 JPQL 查询，一次性获取多个客服的当前 ASSIGNED 工单数：

```java
@Query("SELECT t.assignedAgentId, COUNT(t) FROM TicketEntity t " +
       "WHERE t.assignedAgentId IN :agentIds AND t.status = 'ASSIGNED' GROUP BY t.assignedAgentId")
List<Object[]> countAssignedByAgentIds(@Param("agentIds") List<Long> agentIds);
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile
```

Expected: BUILD SUCCESS

---

### Task 2: 修改 TicketAssignmentService 派单策略

**Files:**
- Modify: `backend/src/main/java/com/cs/customerservice/application/ticket/TicketAssignmentService.java`

- [ ] **Step 1: 理解改动范围**

需要做的事：
1. 删除 `AtomicInteger roundRobinIndex` 字段（不再需要轮询）
2. 删除 `onlineAgents.sort(Comparator.comparingLong(AgentEntity::getId))`
3. 新增按 `createdAt DESC` 排序
4. 查询所有在线客服的当前负载
5. 优先选最新注册且有容量（当前负载 < maxConcurrent）的客服
6. 全部满负载时选负载最低的

- [ ] **Step 2: 重写 TicketAssignmentService**

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
import java.util.*;
import java.util.stream.Collectors;

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
        return Mono.fromRunnable(() -> {
            List<AgentEntity> onlineAgents = agentRepository
                    .findByRoleAndStatus("AGENT", "ONLINE");
            if (onlineAgents.isEmpty()) {
                log.warn("No online agent available for ticket id={}", ticket.getId());
                return;
            }

            // 1. 按注册时间倒序排序（最新注册优先）
            onlineAgents.sort(Comparator.comparing(AgentEntity::getCreatedAt).reversed());

            // 2. 查询所有在线客服的当前已分配工单数
            List<Long> agentIds = onlineAgents.stream()
                    .map(AgentEntity::getId)
                    .collect(Collectors.toList());
            Map<Long, Long> loadMap = ticketRepository.countAssignedByAgentIds(agentIds)
                    .stream()
                    .collect(Collectors.toMap(
                            row -> (Long) row[0],
                            row -> (Long) row[1]
                    ));

            // 3. 选最新注册且有剩余容量的客服
            AgentEntity selected = onlineAgents.stream()
                    .filter(a -> loadMap.getOrDefault(a.getId(), 0L) < a.getMaxConcurrent())
                    .findFirst()
                    .orElseGet(() -> // 全部满负载？选负载最低的
                            onlineAgents.stream()
                                    .min(Comparator.comparingLong(a -> loadMap.getOrDefault(a.getId(), 0L)))
                                    .orElse(onlineAgents.get(0)));

            ticket.setStatus("ASSIGNED");
            ticket.setAssignedAgentId(selected.getId());
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
            log.info("Assigned ticket id={} to agent={} (createdAt={}, load={}/{})",
                    ticket.getId(), selected.getUsername(), selected.getCreatedAt(),
                    loadMap.getOrDefault(selected.getId(), 0L), selected.getMaxConcurrent());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd backend && mvn compile
```

Expected: BUILD SUCCESS

---

### Task 3: 功能验证（手动测试）

**Files:**
- Test: `backend/src/main/resources/data-h2.sql`（种子数据）

- [ ] **Step 1: 确认种子数据中有至少两个客服**

检查 `data-h2.sql` 中是否有 `admin`（TEAM_LEAD）和至少一个 AGENT 角色的客服。需要确保有新老客服可以区分派单。

- [ ] **Step 2: 启动后端服务**

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- [ ] **Step 3: 用 curl 验证派单逻辑**

先登录一个老客服：
```bash
# 登录客服（替换实际用户名）
curl -X POST http://localhost:8080/api/v1/agent/login \
  -H "Content-Type: application/json" \
  -d '{"username":"agent1","password":"123456"}' -v
```

注册一个新客服并登录：
```bash
# 注册新客服（需要主管token，先登录admin）
curl -X POST http://localhost:8080/api/v1/agent/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' -v

# 取 admin 返回的 token，注册新客服
curl -X POST http://localhost:8080/api/v1/agent/register \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin-token>" \
  -d '{"username":"newbie","password":"123456","role":"AGENT"}'

# 新客服登录
curl -X POST http://localhost:8080/api/v1/agent/login \
  -H "Content-Type: application/json" \
  -d '{"username":"newbie","password":"123456"}' -v
```

触发工单自动分配（模拟用户转人工）：
```bash
# 发送消息触发转人工（需要登录token）
curl -X POST http://localhost:8080/api/v1/chat/send \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test-session-1","content":"我要找人工客服","tenantId":"tenant1","userId":"user1"}'
```

查看工单是否分配给新客服：
```bash
curl http://localhost:8080/api/v1/tickets?status=ASSIGNED
```

期望结果：新客服 `newbie` 优先被分配到工单。

- [ ] **Step 4: 测试满负载降级**

重复多次触发转人工，直到新客服接到 5 单（maxConcurrent=5）。第 6 单应分配给老客服。

- [ ] **Step 5: 测试无在线客服**

将所有客服登出，触发转人工，工单应保持 PENDING 状态。

- [ ] **Step 6: 清理测试数据**

```bash
# 删除测试工单和数据（仅用于H2内存数据库，重启即消失）
```

---

### Task 4: Commit

- [ ] **Step 1: Commit 代码**

```bash
cd c:\Users\wenzhenkun\Desktop\xiangmu
git add backend/src/main/java/com/cs/customerservice/application/ticket/TicketAssignmentService.java
git add backend/src/main/java/com/cs/customerservice/infrastructure/repository/TicketRepository.java
git commit -m "feat: 新注册客服优先派单，结合 maxConcurrent 负载保护

- 替换轮询策略为注册时间倒序排序
- 新增 countAssignedByAgentIds 批量查询方法
- 新客服有剩余容量时优先分配
- 全部满负载时选负载最低的客服

Co-Authored-By: Claude <noreply@anthropic.com>"
```
