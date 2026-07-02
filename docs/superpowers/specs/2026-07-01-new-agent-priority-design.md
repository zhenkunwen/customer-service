# 新注册客服优先派单 — 设计文档

## 概要

修改工单自动派单策略，使新注册的客服获得更高的派单优先级，同时利用已有的 `maxConcurrent` 字段防止新客服过载。

## 当前问题

当前 `TicketAssignmentService.autoAssign()` 采用 **轮询（Round-Robin）** 策略，按客服 `id` 升序排列后循环分配。新注册的客服 `id` 更大，排在队尾，反而接单最少，不符合业务需求。

## 方案

采用 **注册时间倒序 + 负载感知** 策略：

1. 所有 `ONLINE` + `role=AGENT` 的客服按 `createdAt DESC`（最新注册优先）排序
2. 查询各客服当前已分配的 ASSIGNED 工单数
3. 选**最新注册且有剩余容量**（当前负载 < `maxConcurrent`）的客服
4. 如果全部满负载，选负载最低的客服

### 修改范围

| 文件 | 改动 |
|------|------|
| `TicketAssignmentService` | 替换排序逻辑 + 新增负载查询 |
| `TicketRepository` | 新增 `countByAssignedAgentIdAndStatus()` |

无需修改：AgentEntity、AgentService、Controller、前端。

### 策略流程

```
新工单进入 autoAssign()
  ↓
查在线客服 (findByRoleAndStatus) — 无变化
  ↓
按 createdAt DESC 排序 — 新逻辑
  ↓
查各客服当前 ASSIGNED 工单数 — 新逻辑
  ↓
选最新且有容量的客服 → 全满则选负载最低的
  ↓
保存分配结果 — 无变化
```

### 边界情况

| 场景 | 行为 |
|------|------|
| 无在线客服 | 工单保持 PENDING，打 warn 日志（同现有） |
| 仅 1 个在线客服 | 直接分配 |
| 新客服已达 maxConcurrent | 自动降级给次新客服 |
| 全部满负载 | 选当前负载最低的 |
| 新客服离线 | 自动跳过，排到下一个 |

## 影响分析

- 仅影响自动派单流程，不影响手动分配和认领
- 无数据迁移，无新增字段
- 新增一次轻量 count 查询，性能可接受
- 兼容现有业务逻辑

## 测试场景

1. 注册一个新客服后上线，新工单应派给该新客服
2. 新客服接满 5 单后，新工单自动派给其他客服
3. 全部客服满负载时，选负载最低的
4. 无在线客服时工单保持 PENDING
5. 手动分配和认领不受影响
