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
      setTickets(res.content ?? []);
      setTotalPages(res.totalPages ?? 0);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [status, page, isAdminView]);

  const loadStats = useCallback(async () => {
    if (!isAdminView) return;
    try { setStats(await getTicketStats()); } catch { /* ignore stats errors */ }
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
                <span className={`text-xs font-medium px-1.5 py-0.5 rounded ${priorityColor(ticket.priority)}`}>P{ticket.priority}</span>
                <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">#{ticket.id}</span>
                <span className="text-xs text-gray-400 font-mono">
                  {ticket.sessionId?.length > 8 ? ticket.sessionId.substring(0, 8) + '…' : ticket.sessionId}
                </span>
              </div>
              <span className="text-xs text-gray-400 whitespace-nowrap ml-2">{fmtTime(ticket.createdAt)}</span>
            </div>
            <p className="text-sm text-gray-600 dark:text-gray-300 mb-3 line-clamp-2 leading-relaxed">{ticket.question}</p>
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2 flex-wrap">
                {ticket.emotionLevel && ticket.emotionLevel !== 'L0' && (
                  <span className={`text-xs px-1.5 py-0.5 rounded ${emotionColor(ticket.emotionLevel)}`}>{ticket.emotionLevel}</span>
                )}
                <span className={`text-xs px-2 py-0.5 rounded ${statusColor(ticket.status)}`}>{statusLabel(ticket.status)}</span>
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
