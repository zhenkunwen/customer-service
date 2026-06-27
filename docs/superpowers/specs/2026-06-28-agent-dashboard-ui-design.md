# 客服管理后台 UI 设计

## 概述

在现有 AI 智能客服系统前端基础上，增加完整的客服管理后台 UI。后端 API 已全部就绪（Agent 认证、工单 CRUD、统计、自动分配），本设计只覆盖前端部分。

## 现有架构

- 前端：React 18 + TypeScript + Vite + Tailwind CSS 3 + Zustand 4
- 后端 API 路径：
  - 客户聊天：`/api/v1/cs/*`（认证：`X-API-Key`）
  - 客服/工单：`/api/v1/agent/*`、`/api/v1/tickets/*`、`/api/v1/admin/*`（认证：`X-Agent-Token`）
- 前端现有布局：`App.tsx` → `ConfigSidebar` + `MessageList` + `ChatInput`
- 前端无路由系统，用状态控制页面切换（ShopPage 模式）

## 新增 UI 架构

### 布局结构

```
App.tsx
├── MainNav                    // 新增：模式切换竖条
├── {mode === 'chat' &&        // 聊天模式（现有，零改动）
│     ConfigSidebar + MessageList + ChatInput}
└── {mode === 'agent' &&       // 客服模式（新增）
      !token ? AgentLogin : AgentDashboard}
```

**MainNav** — 窄竖条（w-14），三个图标按钮纵向排列：
- 💬 客户聊天
- 🎧 客服工作台
- 🌙/☀️ 暗模式切换

### 客服后台内部结构

```
AgentDashboard
├── AgentNav                  // 子导航侧栏（w-48），按角色展示菜单
└── content area              // 根据 currentView 渲染不同页面
    ├── TicketList            // 工单列表（统计栏 + 状态Tab + 卡片列表 + 分页）
    ├── TicketDetail          // 工单详情 + 操作（认领/解决/关闭/派发）
    ├── AgentWorkspace        // 客服工作台（客服列表 + 负载）
    └── AdminPanel            // 管理员面板（注册客服 + 客服列表 + 系统设置）
```

子页面切换用 `currentView` state 控制，不走 react-router-dom，复用 ShopPage 模式。

## 组件详细设计

### 1. MainNav.tsx

| 属性 | 值 |
|------|-----|
| 宽度 | `w-14`（56px） |
| 背景 | `bg-gray-100 dark:bg-gray-800` |
| 内容 | 3 个图标按钮纵向排列，底部留空 |
| 模式切换 | 点击切换 `mode` state（`'chat' | 'agent'`），当前模式高亮 |
| 暗模式 | 底部 🌙/☀️ 按钮，复用 ConfigSidebar 现有逻辑 |

### 2. AgentLogin.tsx

居中卡片式登录表单：

```
┌──────────────────────────┐
│      🎧 客服工作台         │
│                          │
│  用户名: [___________]    │
│  密码:   [___________]    │
│                          │
│  [         登录          ]│
│                          │
│  （错误信息展示区）        │
└──────────────────────────┘
```

- 调 `POST /api/v1/agent/login` → 获取 `{token, role, username}`
- 成功后写入 agentStore + localStorage → 自动切换仪表盘
- 失败后显示内联错误（"用户名或密码错误"）
- 登录中按钮 disable + 显示加载态

### 3. AgentDashboard.tsx

容器组件，维护：
- `currentView: 'tickets' | 'detail' | 'workspace' | 'admin'` state
- `selectedTicket: Ticket | null` — 点开详情时设置
- 子导航 AgentNav 根据 role 渲染菜单项
- content area 根据 currentView 渲染对应页面

### 4. AgentNav.tsx

子导航侧栏（w-48），菜单项按角色动态展示：

| 菜单 | AGENT | TEAM_LEAD | ADMIN |
|------|-------|-----------|-------|
| 📋 工单管理 | ✅ | ✅ | ✅ |
| 👥 客服工作台 | ❌ | ✅ | ✅ |
| ⚙️ 管理面板 | ❌ | ❌ | ✅ |

- 当前菜单项高亮
- 点击切换 currentView
- 工单详情视图中显示「返回列表」入口

### 5. TicketList.tsx

最复杂组件，包含：

**统计栏**（顶部横条）：
- 调 `GET /api/v1/tickets/stats` 获取各状态数量
- 显示：全部 | 待认领 | 处理中 | 已解决 的计数卡片

**状态 Tab**：
- 选项：`全部 | PENDING | ASSIGNED | RESOLVED | CLOSED`
- 切换时重置分页到第 0 页，重新调 `GET /api/v1/tickets?status=X&page=0`

**数据加载**：
- 首次加载：显示骨架屏
- Tab 切换：保留列表不变 + 顶部加载指示器
- 空态：每个 Tab 有独立空消息（"暂无待认领工单" / "暂无处理中工单"）
- 错误态：内联错误消息 + 「重试」按钮

**卡片列表**：
- 每张卡片显示：ID / 优先级标签 / 用户名 / 问题摘要(截断) / 情绪等级 / 状态 / 时间 / 操作按钮
- AGENT 角色：列表来自后端自动过滤（只看自己的）
- 点击卡片 → 跳转到 TicketDetail

**分页**：
- 使用 Spring Data Page 的 page/size 参数
- 简单上一页/下一页 + 当前页号

**操作按钮**（在卡片底部）：
| 状态 | 按钮 | 角色限制 |
|------|------|----------|
| PENDING | [认领] | 全部 |
| PENDING | [派发] | ADMIN/TEAM_LEAD |
| ASSIGNED | [解决] | 本人 |
| ASSIGNED/IN_PROGRESS | [关闭] | 本人/ADMIN |
| RESOLVED | [关闭] | 本人/ADMIN |

### 6. TicketDetail.tsx

单个工单详情视图：

- 「← 返回列表」按钮
- 工单信息：ID / 状态 / 优先级(P0-P3) / 情绪(L0-L3) / 用户 / 时间 / 租户 / 会话ID
- 「问题描述」区块 — 完整显示用户提问
- 「AI 尝试解决」区块 — 显示 AI 已给出的回答
- 「解决方案」区块 — 仅 resolved/closed 状态显示
- 「操作区域」：根据状态和角色显示可用按钮

特殊交互：
- **解决**：点击后展开输入框，输入方案后提交。提交成功后刷新列表并返回
- **关闭**：直接关闭，成功后刷新列表并返回
- **认领**：立即认领，刷新列表
- **派发**：显示客服下拉选择列表 → 选人 → 派发

### 7. AgentWorkspace.tsx（客服工作台）

- 调 `GET /api/v1/agent/loads` 获取全部客服负载
- 表格展示：用户名 / 角色 / 状态 / 当前负载(currentLoad)
- 状态用颜色圆点表示：在线 🟢、离线 🔴
- 每 30 秒自动刷新（参考 useHealth 模式）
- 手动刷新按钮

### 8. AdminPanel.tsx（管理员面板）

三个子 Tab（内部 tab state）：

**Tab 1 — 注册新客服**：
- 用户名输入、密码输入、角色选择（AGENT/TEAM_LEAD）
- 调 `POST /api/v1/agent/register?username=x&password=x&role=x`
- 成功后清空表单 + 显示成功消息
- 参数用 URLSearchParams 格式（后端 @RequestParam）

**Tab 2 — 客服列表**：
- 复用 AgentWorkspace 的负载数据（或直接引用该组件）
- 列表展示全部客服及其当前状态

**Tab 3 — 系统设置**：
- 降级模式状态展示
- 切换按钮调 `POST /api/v1/admin/degradation/toggle`
- 后端健康状态展示（复用 useHealth hook）

## 数据层设计

### agentStore（Zustand）

```typescript
// stores/agentStore.ts
interface AgentStore {
  token: string | null;
  role: string | null;       // 'AGENT' | 'TEAM_LEAD' | 'ADMIN'
  username: string | null;
  setAuth: (token: string, role: string, username: string) => void;
  clearAuth: () => void;
}
```

localStorage 持久化键：
- `cs-agent-token`
- `cs-agent-role`
- `cs-agent-username`

计算属性（store 内辅助函数）：
- `isAdmin(): boolean` — `role === 'ADMIN'`
- `isTeamLead(): boolean` — `role === 'TEAM_LEAD'`
- `isAgent(): boolean` — `role === 'AGENT'`

### agentClient（Axios）

```typescript
// api/agentClient.ts — 完全对标 api/client.ts 的错误处理模式
// 不同点：
const client = axios.create({
  baseURL: '/api/v1',          // 不是 /api/v1/cs
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

// 请求拦截器：从 localStorage 读取 cs-agent-token，注入 X-Agent-Token
// 响应拦截器：
//   - 401 → clearAuth() → 刷新页面（回到登录页）
//   - 其他错误 → 对标 client.ts 的 ApiError 模式
```

### 新增类型（api/types.ts）

```typescript
export interface AgentLoginRequest {
  username: string;
  password: string;
}

export interface AgentLoginResponse {
  token: string;
  role: string;
  username: string;
}

export interface AgentLoadItem {
  id: number;
  username: string;
  role: string;
  status: string;
  currentLoad: number;
}

export interface TicketItem {
  id: number;
  transferEventId: number | null;
  tenantId: string;
  sessionId: string;
  question: string;
  emotionLevel: string;
  topic: string;
  priority: number;
  status: string;
  assignedAgentId: number | null;
  aiAttemptedSolutions: string | null;
  resolution: string | null;
  createdAt: string;   // ISO instant
  updatedAt: string;
}

export interface TicketStats {
  pendingCount: number;
  assignedCount: number;
  inProgressCount: number;
  resolvedCount: number;
  totalCount: number;
}

// Spring Page 包裹类型
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
```

### 新增 API 文件

```typescript
// api/agentAuth.ts
login(req: AgentLoginRequest): Promise<AgentLoginResponse>
logout(): Promise<void>
register(username: string, password: string, role: string): Promise<{id, username, role}>
getLoads(): Promise<AgentLoadItem[]>

// api/ticketApi.ts
list(params: {status?, tenantId?, page?, size?}): Promise<PageResponse<TicketItem>>
getById(id: number): Promise<TicketItem>
claim(id: number): Promise<TicketItem>
assign(id: number, agentId: number): Promise<TicketItem>
resolve(id: number, resolution: string): Promise<TicketItem>
close(id: number): Promise<TicketItem>
del(id: number): Promise<void>
getStats(): Promise<TicketStats>
```

## 状态管理

### 组件内 state vs store

| State | 位置 | 原因 |
|-------|------|------|
| mode（chat/agent） | App.tsx （useState） | 顶级布局切换，无需全局共享 |
| currentView | AgentDashboard（useState） | 仅仪表盘内部用 |
| selectedTicket | AgentDashboard（useState） | 列表→详情传递 |
| token/role/username | agentStore（Zustand） | 持久化、全局需要 |
| isLoading/error | 各组件内（useState） | 局部 UI 状态 |
| stats 数据 | TicketList（useState） | 仅列表需要 |

### 认证流程

```
启动应用 → 检查 localStorage.cs-agent-token
         → 有 → agentStore 恢复 token/role/username → 显示仪表盘
         → 无 → 显示登录页

登录成功 → agentStore.setAuth() → localStorage 持久化 → 显示仪表盘

401 响应 → 拦截器 clearAuth() → 刷新页面 → 回到登录页

登出 → 调 logout API → clearAuth() → 刷新页面
```

## 错误处理

| 场景 | 处理 |
|------|------|
| 登录失败（400） | 表单内联显示错误消息 |
| 登录网络异常 | "网络异常，请检查连接" |
| 工单加载失败 | 列表区域显示错误消息 + 「重试」按钮 |
| 操作失败（400 业务错误） | 页面顶部 toast 或详情区消息 |
| 操作失败（403 无权限） | "无权限执行此操作" |
| Token 过期/无效（401） | 拦截器自动清除 → 刷新到登录页 |
| 空列表 | 每个 Tab 有独立空态文案 |
| 请求进行中 | 按钮 disable + 加载动画 |

## 文件清单

### 修改文件（2 个）

| 文件 | 改动 |
|------|------|
| `frontend/src/App.tsx` | 加 mode state + MainNav + 条件渲染，<40 行 |
| `frontend/src/api/types.ts` | 追加 Agent/Ticket 类型定义，~60 行 |

### 新建文件（10 个）

| 文件 | 行数估计 | 对标 |
|------|----------|------|
| `api/agentClient.ts` | ~70 | clone `client.ts` |
| `api/agentAuth.ts` | ~40 | clone `chat.ts` |
| `api/ticketApi.ts` | ~60 | Spring Page 参数拼接 |
| `stores/agentStore.ts` | ~50 | clone `sessionStore.ts` |
| `components/Layout/MainNav.tsx` | ~40 | 新组件 |
| `pages/AgentLogin.tsx` | ~60 | 新组件 |
| `pages/AgentDashboard.tsx` | ~80 | 仪表盘容器 |
| `components/Agent/TicketList.tsx` | ~200 | 最复杂组件 |
| `components/Agent/TicketDetail.tsx` | ~120 | 详情+操作 |
| `components/Agent/AdminPanel.tsx` | ~120 | 管理员面板 |
| **合计** | **~840** | |

## 实现顺序

1. `types.ts` + `agentStore.ts` + `agentClient.ts` + `agentAuth.ts` + `ticketApi.ts` — 数据层
2. `MainNav.tsx` + 修改 `App.tsx` — 布局
3. `AgentLogin.tsx` — 认证
4. `AgentDashboard.tsx` + `AgentNav.tsx` — 仪表盘壳
5. `TicketList.tsx` — 核心功能
6. `TicketDetail.tsx` — 工单操作
7. `AdminPanel.tsx` — 管理功能

## 不纳入范围

- 实时 WebSocket 推送 — 保持现有轮询模式
- 复杂 RBAC 界面 — 三级角色硬编码，无 UI 内角色编辑
- 告警通知（邮件/钉钉/飞书）
- 工单聊天/对话记录 — 本次只做工单基础操作
- 数据导出/报表
- E2E 测试
