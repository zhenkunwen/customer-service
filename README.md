# AI 智能客服系统

生产级 AI 智能客服后端，基于 Spring Boot 3 WebFlux 响应式架构，集成 DeepSeek 大模型，支持多租户隔离、流式对话、工具调用与事件驱动。

## 特性

- **AI 驱动对话** — 集成 DeepSeek 大模型，支持普通对话、SSE 流式对话、Function Calling 工具调用三种模式
- **多租户架构** — 租户级隔离的 API Key 认证、模型路由与配置，支持金丝雀灰度发布
- **RAG 检索增强** — 知识库检索注入上下文，提升回答准确率
- **Function Calling** — 内置订单查询、物流追踪、退货政策等 AI 工具，LLM 自动决策调用
- **提示注入防护** — 30+ 正则规则过滤 SQL 注入、XSS、越狱攻击，支持租户级自定义敏感词
- **弹性容错** — Resilience4j 限流/熔断/降级/隔离，降级自动触发转人工
- **多层缓存** — Caffeine L1 + Redis L2 两级缓存，降低 LLM 调用延迟
- **事件驱动** — Kafka 异步处理对话记录与转人工事件，含死信队列
- **对话记忆** — Redis 存储多轮对话历史与摘要，LLM 自动总结
- **可观测性** — Micrometer + Prometheus 指标采集，Brave 分布式链路追踪

## 技术栈

| 层 | 技术 |
|------|----------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2, Spring WebFlux, Spring AI |
| AI 模型 | DeepSeek Chat (兼容 OpenAI API) |
| 消息队列 | Apache Kafka |
| 缓存 | Redis (Reactive), Caffeine |
| 数据库 | MySQL 8.0 (JPA + Flyway), H2 (本地开发) |
| 容错 | Resilience4j (限流/熔断/隔离/超时) |
| 监控 | Micrometer, Prometheus, Brave Tracing |
| 前端 | React 18, TypeScript, Vite, Tailwind CSS, Zustand |
| 部署 | Docker Compose, Kubernetes |

## 快速启动

```bash
# 1. 启动基础设施 (MySQL + Redis + Kafka)
docker-compose up -d

# 2. 启动后端 (默认 H2 内存库，无需 MySQL)
cd backend
mvn spring-boot:run

# 3. 启动前端
cd frontend
npm install
npm run dev
```

## 项目结构

```
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/cs/customerservice/
│   │   ├── api/                # REST 控制器 & DTO
│   │   ├── application/        # 业务编排 & 服务
│   │   ├── domain/             # 领域模型
│   │   └── infrastructure/     # 配置、安全、Kafka、持久化
│   └── src/main/resources/     # 配置文件 & 数据库迁移
├── frontend/                   # React 前端
│   └── src/
│       ├── api/                # API 客户端 & 流式处理
│       ├── components/         # UI 组件
│       ├── hooks/              # 自定义 Hooks
│       └── stores/             # Zustand 状态管理
└── k8s/                        # Kubernetes 部署配置
```

## API 接口

| 方法 | 路径 | 说明 |
|--------|------|------|
| POST | `/api/v1/cs/chat` | 普通对话 |
| POST | `/api/v1/cs/chat/stream` | SSE 流式对话 |
| POST | `/api/v1/cs/chat/tool` | 工具调用对话 |

> 所有 API 需在请求头携带 `X-API-Key` 进行鉴权。
> 完整文档见 [api-docs.md](api-docs.md)。

## 多租户

系统内置三个租户，各自独立配置模型、参数与 API Key：

| 租户 | 模型 | 温度 |
|--------|-------|-------------|
| default | deepseek-chat | 0.7 |
| tenant-a | deepseek-chat | 0.5 |
| tenant-b | deepseek-chat | 0.8 |
