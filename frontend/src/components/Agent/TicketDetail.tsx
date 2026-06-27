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
  const role = useAgentStore((s) => s.role);
  const isAdmin = role === 'ADMIN';
  const isTeamLead = role === 'TEAM_LEAD';

  const showError = (err: unknown) => {
    setError(err instanceof ApiError ? err.message : '操作失败');
    setTimeout(() => setError(null), 3000);
  };

  const handleClaim = async () => {
    setLoading(true);
    try { await claimTicket(ticket.id); onUpdated(); }
    catch (err) { showError(err); }
    finally { setLoading(false); }
  };

  const handleResolve = async () => {
    if (!resolution.trim()) return;
    setLoading(true);
    try { await resolveTicket(ticket.id, resolution.trim()); onUpdated(); }
    catch (err) { showError(err); }
    finally { setLoading(false); }
  };

  const handleClose = async () => {
    setLoading(true);
    try { await closeTicket(ticket.id); onUpdated(); }
    catch (err) { showError(err); }
    finally { setLoading(false); }
  };

  const handleAssign = async (targetAgentId: number) => {
    setLoading(true);
    try { await assignTicket(ticket.id, targetAgentId); setShowAssign(false); onUpdated(); }
    catch (err) { showError(err); }
    finally { setLoading(false); }
  };

  const loadAgents = async () => {
    try { setAgents(await getAgentLoads()); setShowAssign(true); }
    catch (err) { showError(err); }
  };

  const statusLabel = ({ PENDING: '待认领', ASSIGNED: '处理中', IN_PROGRESS: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' } as Record<string, string>)[ticket.status] || ticket.status;

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center gap-3 px-6 py-4 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shrink-0">
        <button onClick={onBack} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 text-lg cursor-pointer">←</button>
        <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-100">工单 #{ticket.id}</h2>
        <span className={`text-xs px-2 py-0.5 rounded ${
          ticket.status === 'PENDING' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' :
          ticket.status === 'ASSIGNED' || ticket.status === 'IN_PROGRESS' ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' :
          ticket.status === 'RESOLVED' ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400' :
          'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
        }`}>{statusLabel}</span>
      </div>

      {error && (
        <div className="mx-6 mt-3 px-3 py-2 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-400 rounded-lg text-sm">{error}</div>
      )}

      <div className="flex-1 overflow-auto p-6 space-y-5">
        {/* Meta info */}
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div><span className="text-gray-400">优先级</span><span className="ml-2 font-medium">{'⚡'.repeat(Math.max(1, ticket.priority))} P{ticket.priority}</span></div>
          <div><span className="text-gray-400">情绪等级</span><span className="ml-2 font-medium">{ticket.emotionLevel || 'L0'}</span></div>
          <div><span className="text-gray-400">用户</span><span className="ml-2 font-medium text-gray-700 dark:text-gray-200">{ticket.sessionId?.split('-')[0] || ticket.sessionId}</span></div>
          <div><span className="text-gray-400">租户</span><span className="ml-2 font-medium">{ticket.tenantId}</span></div>
          <div><span className="text-gray-400">创建时间</span><span className="ml-2 font-medium">{fmtFull(ticket.createdAt)}</span></div>
          <div><span className="text-gray-400">更新时间</span><span className="ml-2 font-medium">{fmtFull(ticket.updatedAt)}</span></div>
          {ticket.assignedAgentId && (
            <div className="col-span-2"><span className="text-gray-400">负责客服</span><span className="ml-2 font-medium">#{ticket.assignedAgentId}</span></div>
          )}
        </div>

        {/* Question */}
        <section>
          <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">📝 问题描述</h3>
          <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-4 text-sm text-gray-700 dark:text-gray-300 leading-relaxed whitespace-pre-wrap">{ticket.question}</div>
        </section>

        {/* AI attempts */}
        {ticket.aiAttemptedSolutions && (
          <section>
            <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">🤖 AI 尝试解决</h3>
            <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-4 text-sm text-gray-600 dark:text-gray-400 leading-relaxed whitespace-pre-wrap">{ticket.aiAttemptedSolutions}</div>
          </section>
        )}

        {/* Resolution */}
        {ticket.resolution && (
          <section>
            <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">✅ 解决方案</h3>
            <div className="bg-green-50 dark:bg-green-900/20 rounded-xl p-4 text-sm text-gray-700 dark:text-gray-300 leading-relaxed whitespace-pre-wrap">{ticket.resolution}</div>
          </section>
        )}

        {/* Actions */}
        <section className="border-t border-gray-200 dark:border-gray-700 pt-5">
          <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-3">🔧 操作</h3>
          <div className="flex flex-wrap gap-2">
            {ticket.status === 'PENDING' && (
              <button onClick={handleClaim} disabled={loading}
                className="px-4 py-2 bg-primary-500 text-white rounded-lg text-sm font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors cursor-pointer">
                {loading ? '处理中...' : '📌 认领工单'}
              </button>
            )}
            {(isAdmin || isTeamLead) && ticket.status === 'PENDING' && (
              <button onClick={loadAgents} disabled={loading}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 transition-colors cursor-pointer">
                📤 派发工单
              </button>
            )}
            {(ticket.status === 'ASSIGNED' || ticket.status === 'IN_PROGRESS') && (
              <button onClick={() => setShowResolve(!showResolve)} disabled={loading}
                className="px-4 py-2 bg-green-500 text-white rounded-lg text-sm font-medium hover:bg-green-600 disabled:opacity-50 transition-colors cursor-pointer">
                ✅ 解决工单
              </button>
            )}
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
                placeholder="请输入解决方案..." rows={3}
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
              <button onClick={() => setShowAssign(false)} className="mt-2 text-sm text-gray-400 hover:text-gray-600 cursor-pointer">取消</button>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
