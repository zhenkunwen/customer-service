# AI 智能客服系统

生产级 AI 智能客服后端，基于 Spring Boot 3 WebFlux 响应式架构，集成 DeepSeek 大模型，支持多租户隔离、流式对话、Function Calling 工具调用与事件驱动。

---

## 系统架构

```mermaid
flowchart TB
    User([用户]) -->|SSE / JSON| React[React 前端]
    React -->|POST /api/v1/cs/*| ApiKey[ApiKeyAuthFilter<br/>租户鉴权]
    ApiKey -->|X-API-Key 匹配| Router[ModelRouter<br/>多租户模型路由]
    Router -->|金丝雀灰度| ChatAPI[/api/v1/cs/chat<br/>/chat/stream /chat/tool]
    ChatAPI --> Orchestrator[CustomerChatOrchestrator<br/>核心编排器]
    
    subgraph Orchestrator [编排器内部流程]
        direction TB
        PG[PromptGuard<br/>提示词防护] --> Parrallel{Mono.zip<br/>四路并发}
        Parrallel --> Mem[对话记忆<br/>Redis List]
        Parrallel --> RAG[RAG 知识库]
        Parrallel --> Profile[用户画像]
        Parrallel --> Summary[会话摘要]
        Parrallel --> Classifier[DifficultyClassifier<br/>难度分类]
        Classifier -->|SIMPLE| Light[轻量模型<br/>deepseek-chat]
        Classifier -->|COMPLEX| Strong[强模型<br/>deepseek-reasoner]
    end
    
    subgraph Tools [Function Calling 工具]
        Order[OrderTool<br/>订单查询]
        Logistics[LogisticsTool<br/>物流追踪]
        Refund[RefundTool<br/>退货政策]
    end
    
    Orchestrator -->|工具调用| Tools
    Orchestrator -->|降级回调| Fallback[chatFallback<br/>→ Kafka + 工单]
    
    subgraph Infrastructure [基础设施层]
        MySQL[(MySQL 8.0<br/>JPA / Hibernate)]
        Redis[(Redis 7<br/>缓存 + 会话)]
        Kafka[Kafka<br/>事件总线 + DLQ]
        LLM[DeepSeek API<br/>LLM 服务]
    end
    
    Orchestrator -->|持久化| MySQL
    Orchestrator -->|缓存/记忆| Redis
    Orchestrator -->|事件驱动| Kafka
    Light --> LLM
    Strong --> LLM
    Tools --> MySQL
    
    subgraph Observability [可观测性]
        Prom[Prometheus<br/>指标]
        Trace[Brave<br/>链路追踪]
        Health[Actuator<br/>健康检查]
    end
    
    Prom -.->|采集| Orchestrator
    Trace -.->|追踪| Orchestrator
```

---

## 核心模块设计

### 1. 对话编排流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant F as ApiKeyAuthFilter
    participant C as ChatController
    participant O as CustomerChatOrchestrator
    participant PG as PromptGuard
    participant Cache as 多级缓存
    participant Mem as 对话记忆
    participant DB as 知识库/画像
    participant DC as DifficultyClassifier
    participant LLM as DeepSeek
    
    U->>F: POST /chat (question + sessionId)
    F->>F: X-API-Key → 匹配租户
    F->>C: 放行
    C->>O: chat(request)
    O->>PG: sanitize(question)
    alt 触发规则
        PG-->>C: PromptGuardException → 友好提示
        C-->>U: "输入包含不安全内容"
    end
    O->>Cache: get(tenantId, question)
    alt 缓存命中
        Cache-->>O: 直接返回
        O-->>U: 缓存回答
    else 缓存未命中
        O->>Mem: loadRecentMessages(sessionId, 8)
        O->>Mem: loadSummary(sessionId)
        O->>DB: search(tenantId, question, 3)
        O->>DB: getProfileSummary(tenantId, userId)
        Note over O: Mono.zip 四路并发
        O->>DC: classify(question, emotionLevel)
        alt toolMode=true
            O->>LLM: prompt + tools(order/logistics/refund)
            LLM->>O: tool_calls → 执行工具 → 最终回答
        else
            O->>LLM: prompt + 上下文
            LLM-->>O: 回答
        end
        O->>Mem: appendMessage(user/assistant)
        O->>Cache: put(tenantId, question, answer)
        O->>Kafka: send(chatEvent)
        alt 回答含 [转人工]
            O->>Kafka: send(transferEvent)
            O->>MySQL: createTicket()
        end
        O-->>U: ChatResponse
    end
```

### 2. 工单状态机

```mermaid
stateDiagram-v2
    [*] --> PENDING: AI 判定转人工
    PENDING --> ASSIGNED: 客服认领 / 自动分配
    PENDING --> CLOSED: 管理员关闭
    ASSIGNED --> IN_PROGRESS: 客服开始处理
    ASSIGNED --> RESOLVED: 提交解决方案
    IN_PROGRESS --> RESOLVED: 提交解决方案
    RESOLVED --> CLOSED: 确认关闭
    CLOSED --> [*]
    
    note right of PENDING: L3(愤怒)自动分配<br/>按最小负载策略
    note right of ASSIGNED: 仅认领人可操作
```

### 3. 多租户模型路由

```mermaid
flowchart LR
    Req[请求] --> Key{X-API-Key}
    Key -->|key-a| TA[Tenant-A 配置]
    Key -->|key-b| TB[Tenant-B 配置]
    Key -->|key-default| TD[Default 配置]
    
    TA --> TF{金丝雀灰度}
    TB --> TF
    TD --> TF
    
    TF -->|hash % 100 < 10%| Gray[灰度模型<br/>deepseek-chat-v2]
    TF -->|其余| Stable[稳定模型<br/>deepseek-chat]
    
    Gray --> Client[ChatClient 实例<br/>ConcurrentHashMap 缓存]
    Stable --> Client
```

### 4. 缓存层级

```mermaid
flowchart LR
    Req[用户问题] --> L1[Caffeine L1<br/>本地内存<br/>毫秒级]
    L1 -->|未命中| L2[Redis L2<br/>1h TTL<br/>跨实例共享]
    L2 -->|命中回填 L1| L1
    L2 -->|未命中| LLM[调用 LLM]
    LLM -->|写入 L1+L2| L1
```

### 5. Resilience4j 防护层

```mermaid
flowchart TB
    Req[请求] --> RL[RateLimiter 限流]
    RL --> TL[TimeLimiter 超时 90s]
    TL --> CB[CircuitBreaker 熔断]
    CB --> BH[Bulkhead 舱壁隔离]
    BH --> Biz[业务逻辑]
    
    Biz -->|异常| Fallback[chatFallback<br/>→ 友好提示<br/>→ Kafka 事件<br/>→ 创建工单]
    
    RL -.->|超限| Fallback
    TL -.->|超时| Fallback
    CB -.->|熔断| Fallback
    BH -.->|满额| Fallback
```

---

## 快速启动

### 前置要求

- Docker & Docker Compose
- Java 17+
- Node.js 18+
- DeepSeek API Key ([deepseek.com](https://platform.deepseek.com))

### 1. 启动基础设施

```bash
docker-compose up -d
# 启动 MySQL 8.0 (3307)、Redis 7 (6379)、Kafka (9092)、Zookeeper (2181)
```

### 2. 启动后端（H2 内存模式，无需 MySQL）

```bash
cd backend

# 配置 API Key
export DEEPSEEK_API_KEY=sk-your-key-here

# 启动（默认 h2 profile，自动建表+种子数据）
mvn spring-boot:run
```

> 生产环境使用 MySQL：`mvn spring-boot:run -Dspring-boot.run.profiles=mysql`

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

### 4. 验证

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 测试对话
curl -X POST http://localhost:8080/api/v1/cs/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: default-api-key-change-me" \
  -d '{"sessionId":"test-001","tenantId":"default","userId":"u1","question":"查一下订单 ORD-20240001"}'

# 测试流式
curl -N -X POST http://localhost:8080/api/v1/cs/chat/stream \
  -H "Content-Type: application/json" \
  -H "X-API-Key: default-api-key-change-me" \
  -d '{"sessionId":"test-002","tenantId":"default","userId":"u1","question":"我的快递到哪了"}'
```

---

## 特性清单

| 特性 | 说明 |
|------|------|
| **AI 驱动对话** | 集成 DeepSeek，支持普通对话、SSE 流式、Function Calling 三种模式 |
| **多租户架构** | 租户级 API Key 认证、独立模型配置、金丝雀灰度发布 |
| **工具调用** | 订单查询/物流追踪/退货政策，LLM 自主决策调用 |
| **提示注入防护** | 30+ 正则规则过滤 SQL 注入、XSS、越狱攻击，租户级敏感词 |
| **弹性容错** | Resilience4j 限流/熔断/降级/隔离，降级自动转人工 |
| **多层缓存** | Caffeine L1 + Redis L2 两级缓存，降低 LLM 调用 |
| **事件驱动** | Kafka 异步处理对话记录与转人工事件，含死信队列 |
| **对话记忆** | Redis List 存储多轮历史，LLM 自动摘要压缩 |
| **可观测性** | Prometheus 指标 + Brave 全链路追踪 + Actuator 健康检查 |

## 技术栈

| 维度 | 技术 |
|------|------|
| **语言** | Java 17 |
| **框架** | Spring Boot 3.2, Spring WebFlux, Spring AI |
| **AI 模型** | DeepSeek Chat (兼容 OpenAI API) |
| **消息队列** | Apache Kafka + 死信队列 |
| **缓存** | Redis 7 (Reactive), Caffeine |
| **数据库** | MySQL 8.0 (JPA + Flyway), H2 (本地开发) |
| **容错** | Resilience4j (限流/熔断/隔离/超时) |
| **监控** | Micrometer, Prometheus, Brave Tracing |
| **前端** | React 18, TypeScript, Vite, Tailwind CSS, Zustand |
| **部署** | Docker Compose, Kubernetes (HPA) |

## 项目结构

```
├── backend/                           # Spring Boot 后端
│   └── src/main/java/com/cs/customerservice/
│       ├── api/                       # REST 控制器 & DTO
│       │   ├── controller/            # ChatController, AgentController, TicketController
│       │   └── dto/                   # ChatRequest/Response, TicketDTO
│       ├── application/               # 业务层
│       │   ├── orchestrator/          # CustomerChatOrchestrator (核心编排器)
│       │   ├── ai/                    # DifficultyClassifier
│       │   ├── service/               # 对话记忆、多级缓存、用户画像
│       │   ├── ticket/                # TicketService, TicketAssignmentService
│       │   └── tool/                  # OrderTool, LogisticsTool, RefundTool
│       ├── domain/                    # 领域模型 (KnowledgeChunk)
│       └── infrastructure/            # 基础设施
│           ├── config/                # Cache, Kafka, ChatClient, ModelRouting 配置
│           ├── entity/                # JPA 实体
│           ├── kafka/                 # 生产者/消费者/死信
│           ├── model/                 # ModelRouter
│           ├── repository/            # 数据仓库
│           ├── security/              # API Key / Agent Token 鉴权过滤器
│           └── tracing/               # TraceIdWebFilter
├── frontend/                          # React 前端
│   └── src/
│       ├── api/                       # Axios 客户端、SSE 流式处理
│       ├── components/                # Chat, Agent, Admin 组件
│       ├── hooks/                     # useChat, useStream
│       └── stores/                    # Zustand 状态管理
├── k8s/                               # Kubernetes (Deployment + HPA)
├── docker-compose.yml                 # 基础设施 (MySQL + Redis + Kafka)
└── api-docs.md                        # 完整 API 文档
```

## 部署

### Docker Compose

```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### Kubernetes

```bash
kubectl apply -f k8s/
# 3 副本 Deployment + HPA (3-20 Pod, 基于 CPU/Memory/QPS)
```

---

> 完整 API 文档见 [api-docs.md](api-docs.md)
