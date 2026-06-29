# AI 智能客服系统

生产级 AI 智能客服后端，基于 Spring Boot 3 WebFlux 响应式架构，集成 DeepSeek 大模型，支持多租户隔离、流式对话、Function Calling 工具调用与事件驱动。

---

## 快速启动

### 前置要求
- Docker & Docker Compose、Java 17+、Node.js 18+
- DeepSeek API Key ([platform.deepseek.com](https://platform.deepseek.com))

### 启动

```bash
# 1. 基础设施 (MySQL + Redis + Kafka)
docker-compose up -d

# 2. 后端 (H2 内存模式，无需 MySQL)
cd backend
export DEEPSEEK_API_KEY=sk-your-key-here
mvn spring-boot:run

# 3. 前端
cd frontend
npm install
npm run dev
```

### 验证

```bash
curl -X POST http://localhost:8080/api/v1/cs/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: default-api-key-change-me" \
  -d '{"sessionId":"test-001","tenantId":"default","userId":"u1","question":"查一下订单 ORD-20240001"}'
```

---

## 核心模块

| 模块 | 说明 |
|------|------|
| **编排引擎** | Mono.zip 四路并发加载对话记忆+RAG+用户画像，难度规则分类器 SIMPLE/COMPLEX 两级路由，强/弱模型参数独立配置 |
| **工具调用** | 3 个 Function Calling 工具(订单/物流/退货)，双源查询 + 多级降级兜底，异常分类降级不级联崩溃 |
| **弹性容错** | Resilience4j 四层防护(限流/超时/熔断/舱壁)，全局降级开关一键切换无需重启，降级回调自动写 Kafka + 创建工单 |
| **安全防护** | 自研 PromptGuard 30+ 正则过滤注入/越狱/SQL/XSS/路径遍历，租户级敏感词独立隔离 |
| **基础设施** | Caffeine L1+Redis L2 两级缓存、Kafka 事件驱动含死信队列、Redis 对话记忆自动摘要、Brave 全链路追踪、Prometheus 指标 |
| **工单系统** | 全生命周期管理 PENDING→CLOSED，L3 工单按最小负载自动分配在线客服 |

## 技术栈

| 维度 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2 WebFlux, Spring AI |
| AI 模型 | DeepSeek Chat (OpenAI 兼容) |
| 消息队列 | Apache Kafka + 死信队列 |
| 缓存 | Redis 7 Reactive, Caffeine |
| 数据库 | MySQL 8.0 (JPA + Flyway), H2 (本地开发) |
| 容错 | Resilience4j |
| 监控 | Micrometer, Prometheus, Brave Tracing |
| 前端 | React 18, TypeScript, Vite, Tailwind CSS, Zustand |
| 部署 | Docker Compose, Kubernetes (HPA) |

## 项目结构

```
├── backend/src/main/java/com/cs/customerservice/
│   ├── api/controller/             # REST 控制器
│   ├── application/orchestrator/   # 核心编排器
│   ├── application/ai/             # 难度分类器
│   ├── application/service/        # 对话记忆、多级缓存、用户画像
│   ├── application/ticket/         # 工单服务、自动分配
│   ├── application/tool/           # OrderTool, LogisticsTool, RefundTool
│   └── infrastructure/             # 配置、安全、Kafka、持久化、追踪
├── frontend/src/
│   ├── api/                        # Axios 客户端、SSE 流式处理
│   ├── components/                 # Chat, Agent, Admin 组件
│   └── stores/                     # Zustand 状态管理
├── k8s/                            # Kubernetes (Deployment + HPA)
└── docker-compose.yml
```

## 部署

```bash
# Kubernetes
kubectl apply -f k8s/   # 3 副本 + HPA 3-20 Pod

# Docker Compose 生产
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

---

> 完整 API 文档见 [api-docs.md](api-docs.md)
