import { useState, useEffect, useCallback } from 'react';
import { getAgentLoads } from '@/api/agentAuth';
import { ApiError } from '@/api/client';
import type { AgentLoadItem } from '@/api/types';
import LoadingDots from '@/components/Common/LoadingDots';

export default function AgentWorkspace() {
  const [agents, setAgents] = useState<AgentLoadItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
        const result = await getAgentLoads();
        setAgents(Array.isArray(result) ? result : []);
      }
    catch (err) {
      const msg = err instanceof ApiError ? err.message : '[工作台] 客服列表加载失败';
      console.error('[AgentWorkspace]', err);
      setError(msg);
    }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { const timer = setInterval(load, 30000); return () => clearInterval(timer); }, [load]);

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-100">👥 客服工作台</h2>
        <button onClick={load} disabled={loading}
          className="px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 transition-colors cursor-pointer">
          {loading ? '刷新中...' : '🔄 刷新'}
        </button>
      </div>

      {loading && agents.length === 0 && <LoadingDots />}
      {error && (
        <div className="mb-4 px-3 py-2 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-400 rounded-lg text-sm">{error}</div>
      )}
      {!loading && agents.length === 0 && !error && (
        <div className="text-center text-gray-400 py-10 text-sm">暂无客服</div>
      )}

      <div className="overflow-hidden rounded-xl border border-gray-200 dark:border-gray-700">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 dark:bg-gray-800 text-gray-500 dark:text-gray-400">
              <th className="text-left px-4 py-3 font-medium">ID</th>
              <th className="text-left px-4 py-3 font-medium">用户名</th>
              <th className="text-left px-4 py-3 font-medium">角色</th>
              <th className="text-left px-4 py-3 font-medium">状态</th>
              <th className="text-left px-4 py-3 font-medium">当前负载</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
            {agents.map(a => (
              <tr key={a.id} className="bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-750">
                <td className="px-4 py-3 text-gray-700 dark:text-gray-300 font-mono">{a.id}</td>
                <td className="px-4 py-3 font-medium text-gray-800 dark:text-gray-200">{a.username}</td>
                <td className="px-4 py-3">
                  <span className={`text-xs px-2 py-0.5 rounded ${
                    a.role === 'ADMIN' ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400' :
                    a.role === 'TEAM_LEAD' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' :
                    'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
                  }`}>{a.role}</span>
                </td>
                <td className="px-4 py-3">
                  <span className="flex items-center gap-1.5">
                    <span className={`w-2 h-2 rounded-full ${a.status === 'ONLINE' ? 'bg-green-500' : 'bg-gray-400'}`} />
                    <span className="text-gray-600 dark:text-gray-400">{a.status === 'ONLINE' ? '在线' : '离线'}</span>
                  </span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <div className="w-24 h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                      <div className="h-full bg-primary-500 rounded-full transition-all" style={{ width: `${Math.min(100, a.currentLoad * 20)}%` }} />
                    </div>
                    <span className="text-xs text-gray-500">{a.currentLoad}</span>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-gray-400 mt-3">每 30 秒自动刷新</p>
    </div>
  );
}
