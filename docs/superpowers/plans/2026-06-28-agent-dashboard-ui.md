# 客服管理后台 UI 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有前端聊天应用中增加完整的客服管理后台 UI（登录、工单管理、客服工作台、管理员面板）

**Architecture:** 在现有布局上新增主导航（MainNav）切换聊天/客服两种模式；客服后台用 state 控制子页面切换（类 ShopPage 模式）。后端 API 全部就绪，前端仅新增 10 个文件、修改 2 个文件。

**Tech Stack:** React 18 + TypeScript + Zustand + Tailwind CSS 3 + Axios

---

### Task 1: 数据层 — types + agentStore + agentClient

**Files:**
- Modify: `frontend/src/api/types.ts` — 追加 Agent/Ticket 类型
- Create: `frontend/src/api/agentClient.ts` — 客服 API 客户端（clone `client.ts` 模式）
- Create: `frontend/src/api/agentAuth.ts` — 认证 API（login/logout/register/loads）
- Create: `frontend/src/api/ticketApi.ts` — 工单 API（CRUD + stats）
- Create: `frontend/src/stores/agentStore.ts` — 客服认证状态

- [ ] **Step 1: 在 types.ts 追加类型定义**

追加到 `api/types.ts` 文件末尾：

```typescript
// ==================== Agent / Ticket 类型 ====================

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
  createdAt: string;
  updatedAt: string;
}

export interface TicketStats {
  pendingCount: number;
  assignedCount: number;
  inProgressCount: number;
  resolvedCount: number;
  totalCount: number;
}

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

- [ ] **Step 2: 创建 agentClient.ts**

新建 `api/agentClient.ts`，完全对标 `api/client.ts` 的错误处理，不同点：
- `baseURL: '/api/v1'`（不是 `/api/v1/cs`）
- 请求拦截器注入 `X-Agent-Token`（从 localStorage 读 `cs-agent-token`）
- 响应拦截器：401 → 清除 localStorage 认证信息 → `window.location.reload()`

```typescript
import axios, { AxiosError } from 'axios';
import { ApiError } from './client';

const agentClient = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

agentClient.interceptors.request.use((config) => {
  try {
    const token = localStorage.getItem('cs-agent-token');
    if (token) config.headers['X-Agent-Token'] = token;
  } catch { /* ignore */ }
  return config;
});

agentClient.interceptors.response.use(
  (res) => res,
  (error: AxiosError) => {
    if (axios.isCancel(error)) return Promise.reject(new ApiError('请求已取消', 0));
    if (error.code === 'ECONNABORTED') return Promise.reject(new ApiError('请求超时，请稍后重试', 408));
    if (!error.response) return Promise.reject(new ApiError('网络异常，请检查连接', 0));
    const { status, data } = error.response;
    if (status === 401) {
      localStorage.removeItem('cs-agent-token');
      localStorage.removeItem('cs-agent-role');
      localStorage.removeItem('cs-agent-username');
      window.location.reload();
      return Promise.reject(new ApiError('认证已过期，请重新登录', 401));
    }
    if (status === 403) return Promise.reject(new ApiError(extractMsg(data) || '无权限执行此操作', 403));
    if (status === 400) return Promise.reject(new ApiError(extractMsg(data) || '请求参数有误', 400));
    if (status === 500) return Promise.reject(new ApiError('服务器错误，请稍后重试', 500));
    return Promise.reject(new ApiError('请求失败', status));
  },
);

function extractMsg(data: unknown): string | null {
  if (typeof data === 'object' && data !== null) {
    const d = data as Record<string, unknown>;
    if (typeof d.error === 'string') return d.error;
    if (typeof d.message === 'string') return d.message;
  }
  return null;
}

export default agentClient;
```

- [ ] **Step 3: 创建 agentAuth.ts**

```typescript
import agentClient from './agentClient';
import type { AgentLoginRequest, AgentLoginResponse, AgentLoadItem } from './types';

export async function login(req: AgentLoginRequest): Promise<AgentLoginResponse> {
  const { data } = await agentClient.post<AgentLoginResponse>('/agent/login', req);
  return data;
}

export async function logout(): Promise<void> {
  await agentClient.post('/agent/logout');
}

export async function registerAgent(
  username: string, password: string, role: string
): Promise<{ id: number; username: string; role: string }> {
  const params = new URLSearchParams({ username, password, role });
  const { data } = await agentClient.post('/agent/register', params.toString(), {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });
  return data;
}

export async function getAgentLoads(): Promise<AgentLoadItem[]> {
  const { data } = await agentClient.get<AgentLoadItem[]>('/agent/loads');
  return data;
}

export async function getDegradationStatus(): Promise<{ enabled: boolean }> {
  const { data } = await agentClient.get<{ enabled: boolean }>('/admin/degradation');
  return data;
}

export async function toggleDegradation(): Promise<{ enabled: boolean }> {
  const { data } = await agentClient.post<{ enabled: boolean }>('/admin/degradation/toggle');
  return data;
}
```

- [ ] **Step 4: 创建 ticketApi.ts**

```typescript
import agentClient from './agentClient';
import type { TicketItem, TicketStats, PageResponse } from './types';

export async function listTickets(params?: {
  status?: string; tenantId?: string; page?: number; size?: number;
}): Promise<PageResponse<TicketItem>> {
  const { data } = await agentClient.get<PageResponse<TicketItem>>('/tickets', { params });
  return data;
}

export async function getTicket(id: number): Promise<TicketItem> {
  const { data } = await agentClient.get<TicketItem>(`/tickets/${id}`);
  return data;
}

export async function claimTicket(id: number): Promise<TicketItem> {
  const { data } = await agentClient.put<TicketItem>(`/tickets/${id}/claim`);
  return data;
}

export async function assignTicket(id: number, agentId: number): Promise<TicketItem> {
  const { data } = await agentClient.put<TicketItem>(`/tickets/${id}/assign`, null, {
    params: { agentId },
  });
  return data;
}

export async function resolveTicket(id: number, resolution: string): Promise<TicketItem> {
  const { data } = await agentClient.put<TicketItem>(`/tickets/${id}/resolve`, { resolution });
  return data;
}

export async function closeTicket(id: number): Promise<TicketItem> {
  const { data } = await agentClient.put<TicketItem>(`/tickets/${id}/close`);
  return data;
}

export async function deleteTicket(id: number): Promise<void> {
  await agentClient.delete(`/tickets/${id}`);
}

export async function getTicketStats(): Promise<TicketStats> {
  const { data } = await agentClient.get<TicketStats>('/tickets/stats');
  return data;
}
```

- [ ] **Step 5: 创建 agentStore.ts**

```typescript
import { create } from 'zustand';

interface AgentStore {
  token: string | null;
  role: string | null;
  username: string | null;
  setAuth: (token: string, role: string, username: string) => void;
  clearAuth: () => void;
}

function load(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}

export const useAgentStore = create<AgentStore>((set) => ({
  token: load('cs-agent-token'),
  role: load('cs-agent-role'),
  username: load('cs-agent-username'),

  setAuth: (token, role, username) => {
    try {
      localStorage.setItem('cs-agent-token', token);
      localStorage.setItem('cs-agent-role', role);
      localStorage.setItem('cs-agent-username', username);
    } catch { /* ignore */ }
    set({ token, role, username });
  },

  clearAuth: () => {
    try {
      localStorage.removeItem('cs-agent-token');
      localStorage.removeItem('cs-agent-role');
      localStorage.removeItem('cs-agent-username');
    } catch { /* ignore */ }
    set({ token: null, role: null, username: null });
  },
}));
```

- [ ] **Step 6: 编译验证**

```bash
cd c:/Users/wenzhenkun/Desktop/xiangmu/frontend && npx tsc -b --noEmit 2>&1 | head -30
```
Expected: 无类型错误（或只有未使用变量的 warning）

- [ ] **Step 7: 提交**

```bash
git add frontend/src/api/types.ts frontend/src/api/agentClient.ts frontend/src/api/agentAuth.ts frontend/src/api/ticketApi.ts frontend/src/stores/agentStore.ts
git commit -m "feat: add agent/ticket data layer (types, store, API clients)"
```

---

### Task 2: 布局 — MainNav + App.tsx 改造

**Files:**
- Create: `frontend/src/components/Layout/MainNav.tsx` — 模式切换竖条
- Modify: `frontend/src/App.tsx` — 加 mode state + 条件渲染

- [ ] **Step 1: 创建 MainNav.tsx**

```tsx
import { useAgentStore } from '@/stores/agentStore';

interface MainNavProps {
  mode: 'chat' | 'agent';
  onModeChange: (mode: 'chat' | 'agent') => void;
}

export default function MainNav({ mode, onModeChange }: MainNavProps) {
  const hasToken = useAgentStore((s) => s.token);

  return (
    <nav className="w-14 flex flex-col items-center py-3 gap-2 bg-gray-100 dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 shrink-0">
      <button onClick={() => onModeChange('chat')}
        className={`w-10 h-10 rounded-xl flex items-center justify-center text-lg transition-colors ${
          mode === 'chat'
            ? 'bg-primary-500 text-white shadow-sm'
            : 'text-gray-400 dark:text-gray-500 hover:bg-gray-200 dark:hover:bg-gray-700'
        }`} title="客户聊天">
        💬
      </button>
      <button onClick={() => onModeChange('agent')}
        className={`w-10 h-10 rounded-xl flex items-center justify-center text-lg transition-colors ${
          mode === 'agent'
            ? 'bg-primary-500 text-white shadow-sm'
            : 'text-gray-400 dark:text-gray-500 hover:bg-gray-200 dark:hover:bg-gray-700'
        }`} title="客服工作台">
        🎧
      </button>
    </nav>
  );
}
```

- [ ] **Step 2: 改造 App.tsx**

在文件顶部追加 imports，然后修改 return 结构。

修改 imports 部分：
```typescript
import { useState, useEffect, useRef } from 'react';
import MainNav from '@/components/Layout/MainNav';
import AgentLogin from '@/pages/AgentLogin';
import AgentDashboard from '@/pages/AgentDashboard';
import { useAgentStore } from '@/stores/agentStore';
```

在组件函数开头追加（`const error = ...` 之前）：
```typescript
const [mode, setMode] = useState<'chat' | 'agent'>('chat');
const agentToken = useAgentStore((s) => s.token);
```

修改 return，将外层 `flex-col lg:flex-row` 改为 `flex`，加 `MainNav` 和条件渲染：
```tsx
return (
    <div className="h-screen flex bg-gray-50 dark:bg-gray-900">
      <MainNav mode={mode} onModeChange={setMode} />
      {mode === 'chat' ? (
        <div className="flex-1 flex flex-col lg:flex-row min-w-0">
          <ConfigSidebar />
          <main className="flex-1 flex flex-col min-h-0">
            {error && (
              <div className="mx-4 mt-2 px-4 py-2 bg-red-100 dark:bg-red-900/30 border border-red-300 dark:border-red-700 text-red-700 dark:text-red-300 rounded-lg text-sm flex items-center justify-between">
                <span>❌ {error}</span>
                <button onClick={() => setError(null)} className="ml-2 font-bold">×</button>
              </div>
            )}
            <MessageList />
            <ChatInput ref={inputRef} />
          </main>
        </div>
      ) : agentToken ? (
        <AgentDashboard />
      ) : (
        <AgentLogin />
      )}
    </div>
  );
```

- [ ] **Step 3: 编译验证**

```bash
cd c:/Users/wenzhenkun/Desktop/xiangmu/frontend && npx tsc -b --noEmit 2>&1 | head -30
```
Expected: 可能会报缺少 AgentLogin/AgentDashboard 组件，这是正常的（后续 Task 创建）。除此之外无其他错误。

- [ ] **Step 4: 提交**

```bash
git add frontend/src/components/Layout/MainNav.tsx frontend/src/App.tsx
git commit -m "feat: add MainNav layout and chat/agent mode switching"
```

---

### Task 3: 客服登录页 AgentLogin

**Files:**
- Create: `frontend/src/pages/AgentLogin.tsx`

- [ ] **Step 1: 创建 AgentLogin.tsx**

```tsx
import { useState } from 'react';
import { login } from '@/api/agentAuth';
import { useAgentStore } from '@/stores/agentStore';
import { ApiError } from '@/api/client';

export default function AgentLogin() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const setAuth = useAgentStore((s) => s.setAuth);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const res = await login({ username: username.trim(), password });
      setAuth(res.token, res.role, res.username);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '网络异常，请检查连接');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex-1 flex items-center justify-center bg-gray-50 dark:bg-gray-900">
      <form onSubmit={handleSubmit}
        className="bg-white dark:bg-gray-800 rounded-2xl shadow-lg p-8 w-full max-w-sm border border-gray-200 dark:border-gray-700">
        <div className="text-center mb-6">
          <div className="text-5xl mb-3">🎧</div>
          <h1 className="text-xl font-semibold text-gray-800 dark:text-gray-100">客服工作台</h1>
          <p className="text-sm text-gray-400 mt-1">请登录以管理工单</p>
        </div>
        {error && (
          <div className="mb-4 px-3 py-2 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-400 rounded-lg text-sm">
            {error}
          </div>
        )}
        <input type="text" value={username} onChange={e => setUsername(e.target.value)}
          placeholder="用户名"
          className="w-full px-4 py-3 mb-3 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50"
          disabled={loading} autoFocus />
        <input type="password" value={password} onChange={e => setPassword(e.target.value)}
          placeholder="密码"
          className="w-full px-4 py-3 mb-5 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50"
          disabled={loading} />
        <button type="submit" disabled={loading || !username.trim() || !password.trim()}
          className="w-full py-3 bg-primary-500 text-white rounded-xl font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors cursor-pointer disabled:cursor-not-allowed">
          {loading ? '登录中...' : '登录'}
        </button>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: 编译验证**

```bash
cd c:/Users/wenzhenkun/Desktop/xiangmu/frontend && npx tsc -b --noEmit 2>&1 | head -30
```
Expected: 无类型错误

- [ ] **Step 3: 提交**

```bash
git add frontend/src/pages/AgentLogin.tsx
git commit -m "feat: add agent login page"
```

---

### Task 4: 仪表盘容器 — AgentDashboard + AgentNav

**Files:**
- Create: `frontend/src/pages/AgentDashboard.tsx` — 仪表盘容器（状态管理 + 页面路由）
- Create: `frontend/src/components/Agent/AgentNav.tsx` — 子导航栏

- [ ] **Step 1: 创建 AgentNav.tsx**

```tsx
import { useAgentStore } from '@/stores/agentStore';

type View = 'tickets' | 'detail' | 'workspace' | 'admin';

interface AgentNavProps {
  currentView: View;
  onNavigate: (view: View) => void;
}

const MENU_ITEMS: { view: View; label: string; icon: string; roles: string[] }[] = [
  { view: 'tickets',   label: '工单管理', icon: '📋', roles: ['AGENT', 'TEAM_LEAD', 'ADMIN'] },
  { view: 'workspace', label: '客服工作台', icon: '👥', roles: ['TEAM_LEAD', 'ADMIN'] },
  { view: 'admin',     label: '管理面板', icon: '⚙️', roles: ['ADMIN'] },
];

export default function AgentNav({ currentView, onNavigate }: AgentNavProps) {
  const role = useAgentStore((s) => s.role);
  const visible = MENU_ITEMS.filter(m => m.roles.includes(role || ''));

  return (
    <nav className="w-48 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 p-3 flex flex-col gap-1 shrink-0">
      <div className="text-xs font-medium text-gray-400 uppercase tracking-wide px-3 py-2">客服后台</div>
      {visible.map(item => (
        <button key={item.view} onClick={() => onNavigate(item.view)}
          className={`flex items-center gap-2.5 px-3 py-2.5 rounded-lg text-sm transition-colors text-left ${
            currentView === item.view
              ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300 font-medium'
              : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'
          }`}>
          <span>{item.icon}</span>
          <span>{item.label}</span>
        </button>
      ))}
      <div className="flex-1" />
      <div className="text-xs text-gray-400 px-3 py-2 border-t border-gray-100 dark:border-gray-700 pt-3">
        {role === 'ADMIN' ? '管理员' : role === 'TEAM_LEAD' ? '主管' : '客服'}
      </div>
    </nav>
  );
}
```

- [ ] **Step 2: 创建 AgentDashboard.tsx**

```tsx
import { useState } from 'react';
import AgentNav from '@/components/Agent/AgentNav';
import TicketList from '@/components/Agent/TicketList';
import TicketDetail from '@/components/Agent/TicketDetail';
import AgentWorkspace from '@/components/Agent/AgentWorkspace';
import AdminPanel from '@/components/Agent/AdminPanel';
import type { TicketItem } from '@/api/types';

export type View = 'tickets' | 'detail' | 'workspace' | 'admin';

export default function AgentDashboard() {
  const [view, setView] = useState<View>('tickets');
  const [selectedTicket, setSelectedTicket] = useState<TicketItem | null>(null);

  const openDetail = (ticket: TicketItem) => {
    setSelectedTicket(ticket);
    setView('detail');
  };

  const backToList = () => {
    setSelectedTicket(null);
    setView('tickets');
  };

  return (
    <div className="flex-1 flex min-w-0">
      <AgentNav currentView={view} onNavigate={setView} />
      <div className="flex-1 overflow-auto">
        {view === 'tickets' && <TicketList onOpenTicket={openDetail} />}
        {view === 'detail' && selectedTicket && (
          <TicketDetail ticket={selectedTicket} onBack={backToList} onUpdated={backToList} />
        )}
        {view === 'workspace' && <AgentWorkspace />}
        {view === 'admin' && <AdminPanel />}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: 编译验证**

```bash
cd c:/Users/wenzhenkun/Desktop/xiangmu/frontend && npx tsc -b --noEmit 2>&1 | head -30
```
Expected: 可能会报缺少 TicketList/TicketDetail/AgentWorkspace/AdminPanel 组件，后续 Task 创建

- [ ] **Step 4: 提交**

```bash
git add frontend/src/pages/AgentDashboard.tsx frontend/src/components/Agent/AgentNav.tsx
git commit -m "feat: add agent dashboard container and navigation"
```

---

### Task 5: 工单列表 TicketList

**Files:**
- Create: `frontend/src/components/Agent/TicketList.tsx`

- [ ] **Step 1: 创建 TicketList.tsx**

```tsx
import { useState, useEffect, useCallback } from 'react';
import { useAgentStore } from '@/stores/agentStore';
import { listTickets, claimTicket, closeTicket, getTicketStats } from '@/api/ticketApi';
import { ApiError } from '@/api/client';
import type { TicketItem, TicketStats } from '@/api/types';
import LoadingDots from '@/components/Common/LoadingDots';

interface Props {
  onOpenTicket: (ticket: TicketItem) => void;
}

const STATUS_TABS = [
  { key: '', label: '全部' },
  { key: 'PENDING', label: '待认领' },
  { key: 'ASSIGNED', label: '处理中' },
  { key: 'RESOLVED', label: '已解决' },
  { key: 'CLOSED', label: '已关闭' },
] as const;

function priorityColor(p: number): string {
  if (p >= 3) return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400';
  if (p >= 2) return 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400';
  if (p >= 1) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400';
  return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400';
}

function emotionColor(e: string): string {
  if (e === 'L3') return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400';
  if (e === 'L2') return 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400';
  if (e === 'L1') return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400';
  return '';
}

function statusLabel(s: string): string {
  return ({ PENDING: '待认领', ASSIGNED: '处理中', IN_PROGRESS: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' } as Record<string, string>)[s] || s;
}

function statusColor(s: string): string {
  if (s === 'PENDING') return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400';
  if (s === 'ASSIGNED' || s === 'IN_PROGRESS') return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400';
  if (s === 'RESOLVED') return 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400';
  if (s === 'CLOSED') return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400';
  return '';
}

function fmtTime(iso: string): string {
  try {
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  } catch { return iso; }
}

export default function TicketList({ onOpenTicket }: Props) {
  const [tickets, setTickets] = useState<TicketItem[]>([]);
  const [stats, setStats] = useState<TicketStats | null>(null);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const role = useAgentStore((s) => s.role);
  const isAdminView = role === 'ADMIN' || role === 'TEAM_LEAD';

  const loadTickets = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, string | number> = { page, size: 20 };
      if (isAdminView) {
        if (status) params.status = status;
        params.tenantId = 'default';
      }
      const res = await listTickets(params);
      setTickets(res.content);
      setTotalPages(res.totalPages);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [status, page, isAdminView]);

  const loadStats = useCallback(async () => {
    if (!isAdminView) return;
    try { setStats(await getTicketStats()); } catch { /* ignore */ }
  }, [isAdminView]);

  useEffect(() => { loadTickets(); }, [loadTickets]);
  useEffect(() => { loadStats(); }, []);

  const doAction = async (action: () => Promise<unknown>) => {
    setError(null);
    try {
      await action();
      await Promise.all([loadTickets(), loadStats()]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '操作失败');
    }
  };

  const handleClaim = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    setActionLoading(id);
    await doAction(() => claimTicket(id));
    setActionLoading(null);
  };

  const handleClose = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    setActionLoading(id);
    await doAction(() => closeTicket(id));
    setActionLoading(null);
  };

  return (
    <div className="h-full flex flex-col">
      {/* Stats bar */}
      {isAdminView && (
        <div className="flex gap-3 p-4 pb-2 border-b border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-900/50">
          {STATUS_TABS.map(t => {
            const count = t.key === '' ? stats?.totalCount
              : t.key === 'PENDING' ? stats?.pendingCount
              : t.key === 'ASSIGNED' ? stats?.assignedCount
              : t.key === 'RESOLVED' ? stats?.resolvedCount
              : undefined;
            return (
              <div key={t.key}
                className={`flex-1 rounded-xl p-3 border ${
                  t.key === status
                    ? 'bg-primary-50 dark:bg-primary-900/20 border-primary-200 dark:border-primary-700'
                    : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700'
                }`}>
                <div className="text-2xl font-bold text-gray-800 dark:text-gray-100">{count ?? '-'}</div>
                <div className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{t.label}</div>
              </div>
            );
          })}
        </div>
      )}

      {/* Status tabs */}
      <div className="flex gap-1 px-4 pt-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        {STATUS_TABS.map(t => (
          <button key={t.key} onClick={() => { setStatus(t.key); setPage(0); }}
            className={`px-4 py-2 text-sm rounded-t-lg border border-b-0 transition-colors ${
              status === t.key
                ? 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700 text-primary-600 dark:text-primary-400 font-medium -mb-px'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
            }`}>
            {t.label}
          </button>
        ))}
      </div>

      {/* Error banner */}
      {error && (
        <div className="mx-4 mt-3 px-3 py-2 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-400 rounded-lg text-sm flex items-center justify-between">
          <span>❌ {error}</span>
          <button onClick={() => setError(null)} className="ml-2 font-bold hover:text-red-800">×</button>
        </div>
      )}

      {/* Ticket list */}
      <div className="flex-1 overflow-auto p-4 space-y-3">
        {loading && tickets.length === 0 && (
          <div className="flex items-center justify-center h-32"><LoadingDots /></div>
        )}
        {!loading && !error && tickets.length === 0 && (
          <div className="flex items-center justify-center h-full text-gray-400 dark:text-gray-500 text-sm">
            {status === '' ? '暂无工单' : `暂无「${STATUS_TABS.find(t => t.key === status)?.label}」工单`}
          </div>
        )}
        {tickets.map(ticket => (
          <div key={ticket.id} onClick={() => onOpenTicket(ticket)}
            className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4 cursor-pointer hover:shadow-md hover:border-gray-300 dark:hover:border-gray-600 transition-all">
            <div className="flex items-start justify-between mb-2">
              <div className="flex items-center gap-2 flex-wrap">
                <span className={`text-xs font-medium px-1.5 py-0.5 rounded ${priorityColor(ticket.priority)}`}>
                  P{ticket.priority}
                </span>
                <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">#{ticket.id}</span>
                <span className="text-xs text-gray-400 font-mono">
                  {ticket.sessionId?.length > 8 ? ticket.sessionId.substring(0, 8) + '…' : ticket.sessionId}
                </span>
              </div>
              <span className="text-xs text-gray-400 whitespace-nowrap ml-2">{fmtTime(ticket.createdAt)}</span>
            </div>
            <p className="text-sm text-gray-600 dark:text-gray-300 mb-3 line-clamp-2 leading-relaxed">
              {ticket.question}
            </p>
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2 flex-wrap">
                {ticket.emotionLevel && ticket.emotionLevel !== 'L0' && (
                  <span className={`text-xs px-1.5 py-0.5 rounded ${emotionColor(ticket.emotionLevel)}`}>
                    {ticket.emotionLevel}
                  </span>
                )}
                <span className={`text-xs px-2 py-0.5 rounded ${statusColor(ticket.status)}`}>
                  {statusLabel(ticket.status)}
                </span>
                {ticket.assignedAgentId && (
                  <span className="text-xs text-gray-400">→ 客服 #{ticket.assignedAgentId}</span>
                )}
              </div>
              <div className="flex gap-1.5 shrink-0" onClick={e => e.stopPropagation()}>
                {ticket.status === 'PENDING' && (
                  <button onClick={e => handleClaim(ticket.id, e)} disabled={actionLoading === ticket.id}
                    className="px-3 py-1 text-xs bg-primary-500 text-white rounded-lg hover:bg-primary-600 disabled:opacity-50 transition-colors cursor-pointer">
                    {actionLoading === ticket.id ? '...' : '认领'}
                  </button>
                )}
                {ticket.status === 'RESOLVED' && (
                  <button onClick={e => handleClose(ticket.id, e)} disabled={actionLoading === ticket.id}
                    className="px-3 py-1 text-xs bg-gray-500 text-white rounded-lg hover:bg-gray-600 disabled:opacity-50 transition-colors cursor-pointer">
                    {actionLoading === ticket.id ? '...' : '关闭'}
                  </button>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 p-4 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
          <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
            className="px-3 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded-lg disabled:opacity-30 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors cursor-pointer">
            ‹ 上一页
          </button>
          <span className="text-sm text-gray-500 dark:text-gray-400">{page + 1} / {totalPages}</span>
          <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
            className="px-3 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded-lg disabled:opacity-30 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors cursor-pointer">
            下一页 ›
          </button>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: 编译验证**

```bash
cd c:/Users/wenzhenkun/Desktop/xiangmu/frontend && npx tsc -b --noEmit 2>&1 | head -30
```
Expected: 无类型错误

- [ ] **Step 3: 提交**

```bash
git add frontend/src/components/Agent/TicketList.tsx
git commit -m "feat: add ticket list with stats, tabs, pagination and actions"
```

---

### Task 6: 工单详情 TicketDetail

**Files:**
- Create: `frontend/src/components/Agent/TicketDetail.tsx`

- [ ] **Step 1: 创建 TicketDetail.tsx**

```tsx
import { useState } from 'react';
import { useAgentStore } from '@/stores/agentStore';
import { resolveTicket, closeTicket, assignTicket, claimTicket } from '@/api/ticketApi';
import { getAgentLoads } from '@/api/agentAuth';
import { ApiError } from '@/api/client';
import type { TicketItem, AgentLoadItem } from '@/api/types';

interface Props {
  ticket: TicketItem;
  onBack: () => void;
  onUpdated: () => void;
}

function fmtFull(iso: string): string {
  try {
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  } catch { return iso; }
}

export default function TicketDetail({ ticket, onBack, onUpdated }: Props) {
  const [resolution, setResolution] = useState('');
  const [showResolve, setShowResolve] = useState(false);
  const [showAssign, setShowAssign] = useState(false);
  const [agents, setAgents] = useState<AgentLoadItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 注意：后端从 X-Agent-Token 查当前 agent，前端无需传 agentId
  const role = useAgentStore((s) => s.role);
  const isAdmin = role === 'ADMIN';
  const isTeamLead = role === 'TEAM_LEAD';

  const showError = (err: unknown) => {
    setError(err instanceof ApiError ? err.message : '操作失败');
    setTimeout(() => setError(null), 3000);
  };

  const handleClaim = async () => {
    setLoading(true);
    try {
      await claimTicket(ticket.id);
      onUpdated();
    } catch (err) { showError(err); }
    finally { setLoading(false); }
  };

  const handleResolve = async () => {
    if (!resolution.trim()) return;
    setLoading(true);
    try {
      await resolveTicket(ticket.id, resolution.trim());
      onUpdated();
    } catch (err) { showError(err); }
    finally { setLoading(false); }
  };

  const handleClose = async () => {
    setLoading(true);
    try {
      await closeTicket(ticket.id);
      onUpdated();
    } catch (err) { showError(err); }
    finally { setLoading(false); }
  };

  const handleAssign = async (targetAgentId: number) => {
    setLoading(true);
    try {
      await assignTicket(ticket.id, targetAgentId);
      setShowAssign(false);
      onUpdated();
    } catch (err) { showError(err); }
    finally { setLoading(false); }
  };

  const loadAgents = async () => {
    try {
      setAgents(await getAgentLoads());
      setShowAssign(true);
    } catch (err) { showError(err); }
  };

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center gap-3 px-6 py-4 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shrink-0">
        <button onClick={onBack}
          className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 text-lg cursor-pointer">←</button>
        <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-100">
          工单 #{ticket.id}
        </h2>
        <span className={`text-xs px-2 py-0.5 rounded ${
          ticket.status === 'PENDING' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' :
          ticket.status === 'ASSIGNED' || ticket.status === 'IN_PROGRESS' ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' :
          ticket.status === 'RESOLVED' ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400' :
          'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
        }`}>
          {({ PENDING: '待认领', ASSIGNED: '处理中', IN_PROGRESS: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' } as Record<string, string>)[ticket.status] || ticket.status}
        </span>
      </div>

      {error && (
        <div className="mx-6 mt-3 px-3 py-2 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-400 rounded-lg text-sm">{error}</div>
      )}

      <div className="flex-1 overflow-auto p-6 space-y-5">
        {/* Meta info */}
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div><span className="text-gray-400">优先级</span>
            <span className="ml-2 font-medium">{'⚡'.repeat(Math.max(1, ticket.priority))} P{ticket.priority}</span>
          </div>
          <div><span className="text-gray-400">情绪等级</span>
            <span className="ml-2 font-medium">{ticket.emotionLevel || 'L0'}</span>
          </div>
          <div><span className="text-gray-400">用户</span>
            <span className="ml-2 font-medium text-gray-700 dark:text-gray-200">{ticket.sessionId?.split('-')[0] || ticket.sessionId}</span>
          </div>
          <div><span className="text-gray-400">租户</span>
            <span className="ml-2 font-medium">{ticket.tenantId}</span>
          </div>
          <div><span className="text-gray-400">创建时间</span>
            <span className="ml-2 font-medium">{fmtFull(ticket.createdAt)}</span>
          </div>
          <div><span className="text-gray-400">更新时间</span>
            <span className="ml-2 font-medium">{fmtFull(ticket.updatedAt)}</span>
          </div>
          {ticket.assignedAgentId && (
            <div className="col-span-2"><span className="text-gray-400">负责客服</span>
              <span className="ml-2 font-medium">#{ticket.assignedAgentId}</span>
            </div>
          )}
        </div>

        {/* Question */}
        <section>
          <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">📝 问题描述</h3>
          <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-4 text-sm text-gray-700 dark:text-gray-300 leading-relaxed whitespace-pre-wrap">
            {ticket.question}
          </div>
        </section>

        {/* AI attempts */}
        {ticket.aiAttemptedSolutions && (
          <section>
            <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">🤖 AI 尝试解决</h3>
            <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-4 text-sm text-gray-600 dark:text-gray-400 leading-relaxed whitespace-pre-wrap">
              {ticket.aiAttemptedSolutions}
            </div>
          </section>
        )}

        {/* Resolution */}
        {ticket.resolution && (
          <section>
            <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">✅ 解决方案</h3>
            <div className="bg-green-50 dark:bg-green-900/20 rounded-xl p-4 text-sm text-gray-700 dark:text-gray-300 leading-relaxed whitespace-pre-wrap">
              {ticket.resolution}
            </div>
          </section>
        )}

        {/* Actions */}
        <section className="border-t border-gray-200 dark:border-gray-700 pt-5">
          <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-3">🔧 操作</h3>
          <div className="flex flex-wrap gap-2">
            {/* Claim */}
            {ticket.status === 'PENDING' && (
              <button onClick={handleClaim} disabled={loading}
                className="px-4 py-2 bg-primary-500 text-white rounded-lg text-sm font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors cursor-pointer">
                {loading ? '处理中...' : '📌 认领工单'}
              </button>
            )}

            {/* Assign (admin/team_lead) */}
            {(isAdmin || isTeamLead) && ticket.status === 'PENDING' && (
              <button onClick={loadAgents} disabled={loading}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 transition-colors cursor-pointer">
                📤 派发工单
              </button>
            )}

            {/* Resolve */}
            {(ticket.status === 'ASSIGNED' || ticket.status === 'IN_PROGRESS') && (
              <button onClick={() => setShowResolve(!showResolve)} disabled={loading}
                className="px-4 py-2 bg-green-500 text-white rounded-lg text-sm font-medium hover:bg-green-600 disabled:opacity-50 transition-colors cursor-pointer">
                ✅ 解决工单
              </button>
            )}

            {/* Close */}
            {(ticket.status === 'RESOLVED' || (ticket.status === 'ASSIGNED' && isAdmin)) && (
              <button onClick={handleClose} disabled={loading}
                className="px-4 py-2 bg-gray-500 text-white rounded-lg text-sm font-medium hover:bg-gray-600 disabled:opacity-50 transition-colors cursor-pointer">
                🔒 关闭工单
              </button>
            )}
          </div>

          {/* Resolve form */}
          {showResolve && (
            <div className="mt-4 space-y-2">
              <textarea value={resolution} onChange={e => setResolution(e.target.value)}
                placeholder="请输入解决方案..."
                rows={3}
                className="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
              <button onClick={handleResolve} disabled={loading || !resolution.trim()}
                className="px-4 py-2 bg-green-500 text-white rounded-lg text-sm font-medium hover:bg-green-600 disabled:opacity-50 transition-colors cursor-pointer">
                {loading ? '提交中...' : '提交解决'}
              </button>
            </div>
          )}

          {/* Assign dropdown */}
          {showAssign && (
            <div className="mt-4 p-4 bg-gray-50 dark:bg-gray-900 rounded-xl">
              <h4 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">选择客服</h4>
              <div className="space-y-1">
                {agents.filter(a => a.status === 'ONLINE').map(a => (
                  <button key={a.id} onClick={() => handleAssign(a.id)} disabled={loading}
                    className="block w-full text-left px-3 py-2 rounded-lg text-sm hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors cursor-pointer">
                    #{a.id} {a.username} ({a.role}) — 当前负载: {a.currentLoad}
                  </button>
                ))}
                {agents.filter(a => a.status === 'ONLINE').length === 0 && (
                  <p className="text-sm text-gray-400">暂无在线客服</p>
                )}
              </div>
              <button onClick={() => setShowAssign(false)}
                className="mt-2 text-sm text-gray-400 hover:text-gray-600 cursor-pointer">取消</button>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
```

注意：上面的 `agentId` 是通过 token 解析的占位逻辑。后端通过 `AgentAuthFilter` 将 `AgentEntity` 注入到 `exchange.getAttributes().get("agent")`，前端无法直接获取当前 agent 的 ID。实际认领/解决/关闭时，后端用 exchange 中的 agent 实体判断身份。所以前端传 token 即可，后端会从 Session 中取 agent。

- [ ] **Step 2: 编译验证**

```bash
cd c:/Users/wenzhenkun/Desktop/xiangmu/frontend && npx tsc -b --noEmit 2>&1 | head -30
```
Expected: 无类型错误

- [ ] **Step 3: 提交**

```bash
git add frontend/src/components/Agent/TicketDetail.tsx
git commit -m "feat: add ticket detail view with actions"
```

---

### Task 7: 客服工作台 AgentWorkspace + 管理员面板 AdminPanel

**Files:**
- Create: `frontend/src/components/Agent/AgentWorkspace.tsx` — 客服负载列表
- Create: `frontend/src/components/Agent/AdminPanel.tsx` — 注册客服 + 系统设置

- [ ] **Step 1: 创建 AgentWorkspace.tsx**

```tsx
import { useState, useEffect, useCallback } from 'react';
import { getAgentLoads } from '@/api/agentAuth';
import { ApiError } from '@/api/client';
import type { AgentLoadItem } from '@/api/types';
import LoadingDots from '@/components/Common/LoadingDots';

export default function AgentWorkspace() {
  const [agents, setAgents] = useState<AgentLoadItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setAgents(await getAgentLoads());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // Auto refresh every 30s
  useEffect(() => {
    const timer = setInterval(load, 30000);
    return () => clearInterval(timer);
  }, [load]);

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-100">👥 客服工作台</h2>
        <button onClick={load} disabled={loading}
          className="px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 transition-colors cursor-pointer">
          {loading ? '刷新中...' : '🔄 刷新'}
        </button>
      </div>

      {loading && agents.length === 0 && <LoadingDots />}
      {error && (
        <div className="mb-4 px-3 py-2 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-400 rounded-lg text-sm">
          {error}
        </div>
      )}
      {!loading && agents.length === 0 && !error && (
        <div className="text-center text-gray-400 py-10 text-sm">暂无客服</div>
      )}

      <div className="overflow-hidden rounded-xl border border-gray-200 dark:border-gray-700">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 dark:bg-gray-800 text-gray-500 dark:text-gray-400">
              <th className="text-left px-4 py-3 font-medium">ID</th>
              <th className="text-left px-4 py-3 font-medium">用户名</th>
              <th className="text-left px-4 py-3 font-medium">角色</th>
              <th className="text-left px-4 py-3 font-medium">状态</th>
              <th className="text-left px-4 py-3 font-medium">当前负载</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
            {agents.map(a => (
              <tr key={a.id} className="bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-750">
                <td className="px-4 py-3 text-gray-700 dark:text-gray-300 font-mono">{a.id}</td>
                <td className="px-4 py-3 font-medium text-gray-800 dark:text-gray-200">{a.username}</td>
                <td className="px-4 py-3">
                  <span className={`text-xs px-2 py-0.5 rounded ${
                    a.role === 'ADMIN' ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400' :
                    a.role === 'TEAM_LEAD' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' :
                    'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
                  }`}>{a.role}</span>
                </td>
                <td className="px-4 py-3">
                  <span className="flex items-center gap-1.5">
                    <span className={`w-2 h-2 rounded-full ${a.status === 'ONLINE' ? 'bg-green-500' : 'bg-gray-400'}`} />
                    <span className="text-gray-600 dark:text-gray-400">{a.status === 'ONLINE' ? '在线' : '离线'}</span>
                  </span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <div className="w-24 h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                      <div className="h-full bg-primary-500 rounded-full transition-all" style={{ width: `${Math.min(100, a.currentLoad * 20)}%` }} />
                    </div>
                    <span className="text-xs text-gray-500">{a.currentLoad}</span>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <p className="text-xs text-gray-400 mt-3">每 30 秒自动刷新</p>
    </div>
  );
}
```

- [ ] **Step 2: 创建 AdminPanel.tsx**

```tsx
import { useState, useEffect } from 'react';
import { registerAgent } from '@/api/agentAuth';
import { getDegradationStatus, toggleDegradation } from '@/api/agentAuth';
import { ApiError } from '@/api/client';
import AgentWorkspace from './AgentWorkspace';

type AdminTab = 'register' | 'agents' | 'settings';

function SettingsPanel() {
  const [enabled, setEnabled] = useState(false);
  const [loading, setLoading] = useState(true);
  const [toggling, setToggling] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getDegradationStatus()
      .then(d => { setEnabled(d.enabled); setLoading(false); })
      .catch(() => { setLoading(false); });
  }, []);

  const handleToggle = async () => {
    setToggling(true);
    setError(null);
    try {
      const d = await toggleDegradation();
      setEnabled(d.enabled);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '操作失败');
    } finally {
      setToggling(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
        <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">🔧 系统降级模式</h3>
        <p className="text-xs text-gray-400 mb-3">开启后 AI 对话将使用降级回复，不调用模型</p>
        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-500">
            状态:{' '}
            {loading ? <span className="text-gray-400">加载中...</span> : (
              <span className={enabled ? 'text-red-600 font-medium' : 'text-green-600 font-medium'}>
                {enabled ? '已开启' : '已关闭'}
              </span>
            )}
          </span>
          <button onClick={handleToggle} disabled={loading || toggling}
            className="px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 transition-colors cursor-pointer">
            {toggling ? '切换中...' : '切换'}
          </button>
        </div>
        {error && <p className="text-xs text-red-500 mt-2">{error}</p>}
      </div>
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
        <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">🔄 后端健康状态</h3>
        <p className="text-xs text-gray-400">系统各组件状态见 /actuator/health</p>
      </div>
    </div>
  );
}

export default function AdminPanel() {
  const [tab, setTab] = useState<AdminTab>('register');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('AGENT');
  const [registering, setRegistering] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) return;
    setRegistering(true);
    setMessage(null);
    try {
      const res = await registerAgent(username.trim(), password, role);
      setMessage({ type: 'success', text: `客服「${res.username}」注册成功！` });
      setUsername('');
      setPassword('');
      setRole('AGENT');
    } catch (err) {
      setMessage({ type: 'error', text: err instanceof ApiError ? err.message : '注册失败' });
    } finally {
      setRegistering(false);
    }
  };

  const TABS: { key: AdminTab; label: string }[] = [
    { key: 'register', label: '注册新客服' },
    { key: 'agents', label: '客服列表' },
    { key: 'settings', label: '系统设置' },
  ];

  return (
    <div className="p-6">
      <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">⚙️ 管理面板</h2>

      {/* Tabs */}
      <div className="flex gap-1 mb-6 border-b border-gray-200 dark:border-gray-700">
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`px-4 py-2.5 text-sm rounded-t-lg border border-b-0 transition-colors ${
              tab === t.key
                ? 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700 text-primary-600 dark:text-primary-400 font-medium -mb-px'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
            }`}>
            {t.label}
          </button>
        ))}
      </div>

      {/* Tab: Register */}
      {tab === 'register' && (
        <form onSubmit={handleRegister} className="max-w-md">
          {message && (
            <div className={`mb-4 px-3 py-2 rounded-lg text-sm border ${
              message.type === 'success'
                ? 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800 text-green-600 dark:text-green-400'
                : 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800 text-red-600 dark:text-red-400'
            }`}>
              {message.text}
            </div>
          )}
          <input type="text" value={username} onChange={e => setUsername(e.target.value)}
            placeholder="用户名" disabled={registering}
            className="w-full px-4 py-3 mb-3 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50" />
          <input type="password" value={password} onChange={e => setPassword(e.target.value)}
            placeholder="密码" disabled={registering}
            className="w-full px-4 py-3 mb-3 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50" />
          <select value={role} onChange={e => setRole(e.target.value)} disabled={registering}
            className="w-full px-4 py-3 mb-4 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50">
            <option value="AGENT">客服 (AGENT)</option>
            <option value="TEAM_LEAD">主管 (TEAM_LEAD)</option>
          </select>
          <button type="submit" disabled={registering || !username.trim() || !password.trim()}
            className="px-6 py-3 bg-primary-500 text-white rounded-xl font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors cursor-pointer">
            {registering ? '注册中...' : '注册新客服'}
          </button>
        </form>
      )}

      {/* Tab: Agent list (reuse AgentWorkspace) */}
      {tab === 'agents' && <AgentWorkspace />}

      {/* Tab: Settings */}
      {tab === 'settings' && (
        <div className="max-w-md space-y-4">
          <SettingsPanel />
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: 编译验证**

```bash
cd c:/Users/wenzhenkun/Desktop/xiangmu/frontend && npx tsc -b --noEmit 2>&1 | head -30
```
Expected: 无类型错误，`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add frontend/src/components/Agent/AgentWorkspace.tsx frontend/src/components/Agent/AdminPanel.tsx
git commit -m "feat: add agent workspace and admin panel"
```

---

### Task 8: 最终编译验证

- [ ] **Step 1: 完整编译**

```bash
cd c:/Users/wenzhenkun/Desktop/xiangmu/frontend && npx tsc -b --noEmit 2>&1
```
Expected: 无错误，`BUILD SUCCESS`

- [ ] **Step 2: 开发服务器启动确认**

```bash
cd c:/Users/wenzhenkun/Desktop/xiangmu/frontend && timeout 10 npm run dev 2>&1 || true
```
Expected: Vite dev server 启动在 `http://localhost:3000`

- [ ] **Step 3: 提交（如有修复）**

```bash
git add -A && git commit -m "chore: fix compilation issues"
```
