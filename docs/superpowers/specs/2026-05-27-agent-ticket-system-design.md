# 人工接管系统设计

## 概述

在现有 AI 智能客服系统基础上，增加完整的人工接管能力：客服账号认证、工单管理、自动分配和 Kafka 事件通知。

## 角色模型

| 角色 | 权限 |
|------|------|
| ADMIN | 全局数据、分配工单、删除工单、管理客服账号 |
| TEAM_LEAD | 组内数据、派发工单、查看客服负载 |
| AGENT | 处理分配给自己的工单、认领池内工单 |

## 数据模型

### agents 表

```sql
CREATE TABLE agents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    role ENUM('ADMIN','TEAM_LEAD','AGENT') NOT NULL,
    status ENUM('ONLINE','OFFLINE','BUSY') DEFAULT 'OFFLINE',
    max_concurrent INT DEFAULT 5,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### tickets 表

```sql
CREATE TABLE tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_event_id BIGINT,
    tenant_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    question TEXT,
    emotion_level VARCHAR(8) DEFAULT 'L0',
    topic VARCHAR(128),
    priority INT DEFAULT 0,
    status ENUM('PENDING','ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED') DEFAULT 'PENDING',
    assigned_agent_id BIGINT,
    ai_attempted_solutions TEXT,
    resolution TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 状态流转

```
PENDING → (认领/派发) → ASSIGNED → (开始处理) → IN_PROGRESS → (解决) → RESOLVED → (关闭) → CLOSED
```

## 自动分配

转人工触发时，Orchestrator 的 `postProcess()` 检测到 `[转人工]` 后新增创建工单步骤：

- priority = L3 → 立即分配（找负载最低的在线 AGENT）+ Kafka 通知
- priority = L0/L1/L2 → PENDING 状态，进入待认领池

分配策略：`AgentRepository` 按 currentLoad 排序取最低的在线客服。复用现有 Kafka 基础设施发送 `ticket-events`。

## API 设计

### 客服认证

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/api/v1/agent/login` | 登录返回 JWT | 所有 |
| POST | `/api/v1/agent/register` | 注册客服 | ADMIN |

### 工单管理

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| GET | `/api/v1/tickets` | 分页列表（按状态/租户筛选） | 所有 |
| GET | `/api/v1/tickets/{id}` | 工单详情 | 所有 |
| PUT | `/api/v1/tickets/{id}/claim` | 认领 | AGENT/TEAM_LEAD |
| PUT | `/api/v1/tickets/{id}/assign?agentId={id}` | 派发 | TEAM_LEAD/ADMIN |
| PUT | `/api/v1/tickets/{id}/resolve` | 提交解决 | 责任人 |
| PUT | `/api/v1/tickets/{id}/close` | 关闭 | 责任人/ADMIN |
| GET | `/api/v1/tickets/stats` | 统计 | ADMIN/TEAM_LEAD |

认证方式：`X-Agent-Token` Header + JWT，经由 `AgentAuthFilter`（WebFilter，复用现有 `ApiKeyAuthFilter` 模式）。

## 新增文件清单

### controllers
- `api/controller/AgentController.java`
- `api/controller/TicketController.java`

### DTOs
- `api/dto/AgentLoginRequest.java`
- `api/dto/AgentLoginResponse.java`
- `api/dto/TicketResponse.java`
- `api/dto/TicketUpdateRequest.java`

### services
- `application/agent/AgentService.java`
- `application/ticket/TicketService.java`
- `application/ticket/TicketAssignmentService.java`

### entities & repos
- `infrastructure/entity/AgentEntity.java`
- `infrastructure/entity/TicketEntity.java`
- `infrastructure/repository/AgentRepository.java`
- `infrastructure/repository/TicketRepository.java`

### security
- `infrastructure/security/AgentAuthFilter.java`
- `infrastructure/security/AgentTokenService.java`

### migration
- `db/migration/V4__agent_ticket.sql`

### existing file modifications
- `application/orchestrator/CustomerChatOrchestrator.java` — postProcess 中集成创建工单

## 不纳入范围

- 实时 WebSocket 推送 → 现阶段用 Kafka 事件 + 客服轮询
- 复杂 RBAC → 三级角色硬编码枚举
- 告警推送（邮件/钉钉/飞书）→ 后续单独设计
- 管理后台 UI → 后续单独设计
