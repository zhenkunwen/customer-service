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
