# 面试介绍指南

## 一句话定位

一个生产级 AI 智能客服系统，接 DeepSeek 大模型，支持工具调用、多租户路由、流式对话、自动转人工。后端 Spring WebFlux 全异步，前端 React + TypeScript。从 LLM 接入到熔断降级到工单系统完整闭环。

---

## 三分钟介绍

### 1. 解决什么问题

电商平台客服咨询量大、重复性高。系统用大模型自动处理订单查询、物流追踪、退款政策等高频问题，处理不了的无感转人工，目标替代 70% 的重复人工咨询。

### 2. 技术架构

后端 Spring Boot 3.2 + WebFlux 全异步非阻塞。AI 层接 DeepSeek，通过 Spring AI 做函数调用（Function Calling），大模型能自主决定调哪个工具查数据库。前端 React + Vite + Zustand，支持 SSE 流式输出。

### 3. 核心亮点

**多租户模型路由**：不同租户独立配置模型名、temperature、maxTokens、API Key 和 Base URL。ModelRouter 按 tenantId 动态创建 ChatClient 实例，支持金丝雀发布——按 user hash 把流量百分比切到新模型。

**四层容错降级**：Resilience4j（限流 + 熔断 + 隔舱 + 超时）围在核心对话方法上。熔断触发自动返回兜底话术。管理员可一键切降级模式，所有请求直接走安抚文案。

**异步事件驱动**：对话记录和转人工事件通过 Kafka 异步写入，不阻塞主链路。Kafka 不可用时 5 秒超时兜底，h2 本地开发直接 skip Kafka Bean 创建。

**DDD 分层架构**：api → application(orchestrator/service/tool) → domain → infrastructure，5670 行核心编排器组合所有服务完成完整对话流程。

### 4. 数据

工具调用延迟 2-3 秒，流式首 token 1 秒内。67 个 Java 源文件 + 23 个前端文件，18 个 Git 提交从零搭建到完整系统。

---

## 追问准备

### Q: 为什么不直接用 OpenAI？

DeepSeek 兼容 OpenAI API 格式，成本低很多。Spring AI 抽象了调用细节，换模型只改 yml 配置。

### Q: Function Calling 怎么实现的？

Spring AI 自动把 Function<Req, Resp> Bean 包装成 ToolCallback，从 record 的 Jackson 注解生成 JSON Schema，传给 DeepSeek。模型返回 tool_calls → Spring AI 执行 → 结果回传 → 模型给最终答案。

### Q: 怎么保证多租户安全？

双通道鉴权：消费者 X-API-Key 匹配租户，客服 X-Agent-Token。PromptGuardService 30+ 正则防注入（SQL 注入、XSS、越狱），租户级自定义敏感词。

### Q: 对话记忆怎么做的？

Redis List 存多轮历史，超 20 轮触发 LLM 摘要压缩，12h TTL。两层缓存 Caffeine(L1) + Redis(L2) 按租户隔离。

### Q: 有哪些不足？怎么改进？

- 工具数量有限，可扩展更多业务工具
- RAG 还是内存适配器，应接向量数据库
- 缺少对话质量评估（可接 eval 模型做事后打分）
- 这也是展示思考深度的好机会——主动说出不足说明你能自我批判

### Q: 运维怎么考虑？

- Docker Compose 一键本地启动（MySQL + Redis + Kafka + Zookeeper）
- K8s deployment + HPA 自动扩缩容
- Micrometer + Prometheus 暴露指标，Zipkin 全链路追踪
- 健康检查 endpoint 给 K8s liveness probe

---

## 现场演示流程

1. 前端输入「帮我查订单 ORD-20240001，用户 ID 是 user-001」
2. 展示工具调用过程（日志可见 LogisticsTool/OrderTool called）
3. 流式输出回答
4. 切换话题问退款政策
5. 输入「转人工」触发工单创建

---

## 实际踩坑（加分项）

### DeepSeek Connection Reset

JDK HttpClient 发带 tools 的大 POST 请求到 DeepSeek HTTPS 时 Connection reset。排查过程：先用 Python 直调 DeepSeek 确认 API 正常 → 排除 API 层 → 定位到 Spring 的 RestClient Builder 用了 JDK HttpClient → 去掉后恢复正常。

这个排查过程展示了**诊断先于修复**的工程思维，面试官最喜欢听这种。

### Kafka 阻塞 h2 开发

h2 profile 无 Kafka 运行时，同步 send 阻塞 60 秒超时。改为 `subscribeOn(boundedElastic) + timeout(5s) + onErrorResume`，Kafka 不可用时静默跳过。

---

## 面试禁忌

1. 不要主动说"还没上线"——没人问就不用提
2. 不要说"调 API"——说"大模型自主决策调用工具，Agent 模式"
3. 不要把所有技术名词堆在一起念——2-3 个亮点深度展开比 10 个名词一笔带过强
4. 准备一个可演示的完整流程——眼见为实
