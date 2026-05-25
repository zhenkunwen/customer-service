import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';
import { postNormalChat, postToolChat } from '@/api/chat';

vi.mock('axios');

beforeEach(() => {
  vi.clearAllMocks();
});

describe('chat API', () => {
  it('postNormalChat success', async () => {
    const mockRes = {
      data: {
        sessionId: 's1',
        answer: '你好',
        model: 'deepseek-chat',
        latencyMs: 500,
        fallback: false,
        toolCalls: null,
      },
    };
    vi.mocked(axios.post).mockResolvedValueOnce(mockRes);

    const res = await postNormalChat({
      sessionId: 's1', tenantId: 'default', userId: 'u1',
      question: '你好', streamMode: false, toolMode: false,
    });

    expect(res.answer).toBe('你好');
    expect(res.fallback).toBe(false);
  });

  it('postToolChat success with toolCalls', async () => {
    const mockRes = {
      data: {
        sessionId: 's1',
        answer: '查到订单',
        model: 'deepseek-chat',
        latencyMs: 800,
        fallback: false,
        toolCalls: [{ toolName: 'orderTool', arguments: '{}', result: '{}' }],
      },
    };
    vi.mocked(axios.post).mockResolvedValueOnce(mockRes);

    const res = await postToolChat({
      sessionId: 's1', tenantId: 'default', userId: 'u1',
      question: '查一下我的订单', streamMode: false, toolMode: true,
    });

    expect(res.toolCalls).toHaveLength(1);
    expect(res.toolCalls![0].toolName).toBe('orderTool');
  });
});
