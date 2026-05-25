import client from './client';
import type { ChatRequest, ChatResponse } from './types';

export async function postNormalChat(req: ChatRequest, signal?: AbortSignal): Promise<ChatResponse> {
  const { data } = await client.post<ChatResponse>('/chat', {
    ...req,
    streamMode: false,
    toolMode: false,
  }, { signal });
  return data;
}

export async function postToolChat(req: ChatRequest, signal?: AbortSignal): Promise<ChatResponse> {
  const { data } = await client.post<ChatResponse>('/chat/tool', {
    ...req,
    streamMode: false,
    toolMode: true,
  }, { signal });
  return data;
}
