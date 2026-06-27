// ==================== 请求类型 ====================

export interface ChatRequest {
  sessionId: string;
  tenantId: string;
  userId: string;
  question: string;
  streamMode: boolean;
  toolMode: boolean;
}

// ==================== 响应类型 ====================

export interface ToolCallRecord {
  toolName: string;
  arguments: string; // JSON string
  result: string;    // JSON string
}

export interface ChatResponse {
  sessionId: string;
  answer: string;
  model: string;
  toolCalls: ToolCallRecord[] | null;
  latencyMs: number;
  fallback: boolean;
}

// ==================== 流式事件 ====================

export interface StreamTokenEvent {
  event: 'token';
  data: string;
}

export interface StreamDoneEvent {
  event: 'done';
  data: '[DONE]';
}

export interface StreamErrorEvent {
  event: 'error';
  data: string;
}

export type StreamEvent = StreamTokenEvent | StreamDoneEvent | StreamErrorEvent;

// ==================== 消息类型 ====================

export type MessageRole = 'user' | 'assistant' | 'tool' | 'system';

export interface Message {
  id: string;
  role: MessageRole;
  content: string;
  model?: string;
  latencyMs?: number;
  fallback?: boolean;
  toolCalls?: ToolCallRecord[];
  timestamp: number;
}

// ==================== 会话类型 ====================

export interface SessionConfig {
  sessionId: string;
  tenantId: string;
  userId: string;
  mode: 'normal' | 'stream' | 'tool';
}

export type TenantId = 'default' | 'tenant-a' | 'tenant-b';

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

export interface ChatRecord {
  id: number;
  userId: string;
  model: string;
  question: string;
  answer: string;
  latencyMs: number;
  status: string;
  createdAt: string;
}
