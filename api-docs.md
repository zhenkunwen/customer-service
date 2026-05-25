# 智能客服后端 API 文档

## 服务信息

| 项目 | 值 |
|------|-----|
| 框架 | Spring Boot 3.2.5 + WebFlux (Reactive) |
| 端口 | 8080 |
| 基础路径 | `/api/v1/cs` |
| 返回格式 | JSON |
| 字符编码 | UTF-8 |
| Swagger UI | `http://localhost:8080/webjars/swagger-ui/index.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |

---

## 鉴权方式

所有 `/api/` 路径的请求需要在请求头中携带 `X-API-Key`。

| Header | 必填 | 说明 |
|--------|:----:|------|
| X-API-Key | 是 | 租户 API Key，用于多租户鉴权 |

### 可用 Key

| 租户 | Key（开发环境默认值） |
|------|----------------------|
| default | `default-api-key-change-me` |
| tenant-a | `tenant-a-key-change-me` |
| tenant-b | `tenant-b-key-change-me` |

**注意**：生产环境请通过环境变量 `TENANT_DEFAULT_KEY`、`TENANT_A_KEY`、`TENANT_B_KEY` 配置。

---

## 端点 1：普通对话

**请求**

```
POST /api/v1/cs/chat
Content-Type: application/json
X-API-Key: <tenant-api-key>
```

**请求体 (JSON)**

```json
{
  "sessionId": "string, 必填, 会话ID",
  "tenantId": "string, 必填, 租户ID，支持 default / tenant-a / tenant-b",
  "userId": "string, 必填, 用户ID",
  "question": "string, 必填, 用户问题",
  "streamMode": false,
  "toolMode": false
}
```

**响应体 (JSON)**

```json
{
  "sessionId": "string, 会话ID",
  "answer": "string, AI 回复内容",
  "model": "string, 使用的模型名称，如 deepseek-chat",
  "toolCalls": null,
  "latencyMs": 1234,
  "fallback": false,
  "traceId": "string, 分布式追踪ID"
}
```

**响应体 - 降级（系统过载时）**

```json
{
  "sessionId": "xxx",
  "answer": "抱歉，当前咨询人数较多，系统暂时无法响应...",
  "model": "fallback",
  "toolCalls": null,
  "latencyMs": 0,
  "fallback": true,
  "traceId": "string"
}
```

**响应体 - 内容审核拦截**

```json
{
  "sessionId": "xxx",
  "answer": "输入包含不安全内容，请修改后重试",
  "model": "guard",
  "toolCalls": null,
  "latencyMs": 0,
  "fallback": true,
  "traceId": "string"
}
```

---

## 端点 2：流式对话 (SSE)

```
POST /api/v1/cs/chat/stream
Content-Type: application/json
X-API-Key: <tenant-api-key>
Accept: text/event-stream
```

**请求体同上**

**响应格式 (Server-Sent Events)**

首条事件为追踪信息，后续为流式文本片段：

```
event:trace
data:a1b2c3d4e5f6g7h8

event:token
data:你

event:token
data:好

event:token
data:，

event:done
data:[DONE]

// 出错时
event:error
data:输入包含不安全内容，请修改后重试
```

| 事件类型 | 说明 |
|---------|------|
| `trace` | 分布式追踪 ID，首条发送 |
| `token` | 一个文本片段（一个字或一个词） |
| `done` | 流式结束信号 |
| `error` | 错误信息 |

---

## 端点 3：带工具调用对话

```
POST /api/v1/cs/chat/tool
Content-Type: application/json
X-API-Key: <tenant-api-key>
```

**请求体同上（toolMode 强制为 true）**

**响应体**

```json
{
  "sessionId": "string",
  "answer": "string, AI 最终回复",
  "model": "deepseek-chat",
  "toolCalls": [
    {
      "toolName": "orderTool",
      "arguments": "{\"userId\":\"xxx\",\"orderId\":\"ORD-20240001\"}",
      "result": "{\"orderId\":\"ORD-20240001\",\"status\":\"已发货\",\"amount\":\"299.00\"}"
    }
  ],
  "latencyMs": 2345,
  "fallback": false,
  "traceId": "string, 分布式追踪ID"
}
```

---

## 可用工具列表（供 toolMode 使用）

工具数据存储在 MySQL 数据库中，通过 JPA 仓库查询。

### 1. orderTool — 查询订单

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| userId | String | 是 | 用户ID |
| orderId | String | 是 | 订单ID |

种子数据：

| orderId | 用户 | 状态 | 金额 | 详情 |
|---------|------|------|------|------|
| ORD-20240001 | user-001 | 已发货 | 299.00 | 蓝牙耳机 x1，收货：上海浦东 |
| ORD-20240002 | user-001 | 待付款 | 1599.00 | 机械键盘 x1，30分钟内支付 |
| ORD-20240003 | user-002 | 已完成 | 49.90 | 手机壳 x2，已签收 |

### 2. logisticsTool — 查询物流

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| orderId | String | 是 | 订单ID |

种子数据（仅 ORD-20240001 有物流）：

| orderId | 快递 | 运单号 | 状态 | 轨迹 |
|---------|------|--------|------|------|
| ORD-20240001 | 顺丰速运 | SF1234567890 | 运输中 | 已揽收→上海分拣中心→杭州中转站 |

### 3. refundTool — 退款政策查询

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| productType | String | 是 | 商品类型（电子产品/服饰/食品/日用品） |

退款政策：

| productType | 退货天数 | 条件 |
|-------------|:------:|------|
| 电子产品 | 7 | 包装完好、配件齐全、无人为损坏；激活后不支持 |
| 服饰 | 15 | 吊牌完好、未洗涤、无污渍；内衣拆封后不支持 |
| 食品 | 7 | 未拆封、保质期内；生鲜签收后不支持 |
| 日用品 | 7 | 未拆封、未使用；个人护理拆封后不支持 |
| 通用（默认） | 7 | 商品完好、附购买凭证 |

---

## 健康检查

```
GET /actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP", "details": {"database": "MySQL"}},
    "redis": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

## 监控指标

```
GET /actuator/prometheus
```

返回 Prometheus 格式的指标数据。

---

## 租户信息

| tenantId | 模型 | temperature | maxTokens | 函数调用 |
|----------|------|-------------|-----------|---------|
| default | deepseek-chat | 0.7 | 2048 | 启用 |
| tenant-a | deepseek-chat | 0.5 | 1024 | 启用 |
| tenant-b | deepseek-chat | 0.8 | 4096 | 启用 |

---

## 错误码

| HTTP 状态 | 含义 |
|-----------|------|
| 200 | 成功 |
| 400 | 请求参数校验失败（sessionId/tenantId/userId/question 缺少） |
| 401 | 缺少 API Key（未提供 X-API-Key 头） |
| 403 | API Key 无效（提供的 Key 与任意租户不匹配） |
| 404 | 路径不存在 |
| 415 | 不支持的 Content-Type（需使用 application/json） |
| 429 | 请求频率超限（RateLimiter 触发） |
| 500 | 服务内部错误 |

---

## 超时设置

| 接口 | 超时 |
|------|------|
| /chat | 35s |
| /chat/stream | 60s |
| /chat/tool | 35s |
