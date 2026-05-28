import type { ChatRequest, StreamEvent } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1/cs';

const defaultApiKeys: Record<string, string> = {
  default: 'change-me',
  'tenant-a': 'change-me',
  'tenant-b': 'change-me',
};

function getApiKey(): string {
  try {
    const tenantId = localStorage.getItem('cs-tenant-id') || 'default';
    const saved = localStorage.getItem(`cs-apikey-${tenantId}`);
    return saved || defaultApiKeys[tenantId] || '';
  } catch {
    return 'change-me';
  }
}

/**
 * 发起流式对话，返回 AsyncGenerator<StreamEvent>
 * 调用方通过 for await...of 迭代事件
 */
export async function* postStreamChat(
  req: ChatRequest,
  signal?: AbortSignal,
): AsyncGenerator<StreamEvent, void, undefined> {
  const url = `${BASE_URL}/chat/stream`;

  // 合并 AbortSignal：用户中断 + 120s 超时（后端 90s TimeLimiter）
  let combinedSignal = signal;
  try {
    const timeoutSignal = AbortSignal.timeout(120000);
    combinedSignal = signal
      ? AbortSignal.any([signal, timeoutSignal])
      : timeoutSignal;
  } catch {
    // AbortSignal.any/timeout 可能不被旧浏览器支持，降级使用原始 signal
  }

  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': getApiKey(),
    },
    body: JSON.stringify({ ...req, streamMode: true, toolMode: false }),
    signal: combinedSignal,
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`流式请求失败 (${res.status}): ${text}`);
  }

  if (!res.body) {
    throw new Error('浏览器不支持流式响应');
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    let currentEventType = '';
    let currentData = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line === '' || line === '\r') {
          // SSE 空行 = 事件结束，yield 累积的事件
          if (currentEventType && (currentEventType === 'token' || currentEventType === 'done' || currentEventType === 'error')) {
            yield { event: currentEventType, data: currentData } as StreamEvent;
          }
          currentEventType = '';
          currentData = '';
        } else if (line.startsWith('event:')) {
          currentEventType = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          const chunk = line.slice(5).replace(/^ /, '');
          currentData = currentData ? currentData + '\n' + chunk : chunk;
        }
        // 忽略注释行（以 : 开头）
      }
    }
  } finally {
    reader.releaseLock();
  }
}
