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
