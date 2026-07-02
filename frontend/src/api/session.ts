import client from './client';
import type { ChatRecord } from './types';

export async function getSessionMessages(sessionId: string): Promise<ChatRecord[]> {
  const { data } = await client.get<ChatRecord[]>(`/sessions/${sessionId}/messages`);
  return data;
}
