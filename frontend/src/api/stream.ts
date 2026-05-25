import type { ChatRequest, StreamEvent } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1/cs';

function getApiKey(): string {
  try {
    const tenantId = localStorage.getItem('cs-tenant-id') || 'default';
    return localStorage.getItem(`cs-apikey-${tenantId}`)
      || '';
  } catch {
    return '';
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

  // 合并 AbortSignal：用户中断 + 65s 超时（后端 60s）
  let combinedSignal = signal;
  try {
    const timeoutSignal = AbortSignal.timeout(65000);
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
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      let currentEvent = '';
      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          const data = line.slice(5).trim();
          if (currentEvent === 'token' || currentEvent === 'done' || currentEvent === 'error') {
            yield { event: currentEvent, data } as StreamEvent;
          }
          currentEvent = '';
        }
      }
    }
  } finally {
    reader.releaseLock();
  }
}
