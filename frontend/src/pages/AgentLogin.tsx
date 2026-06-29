import { useState } from 'react';
import { login } from '@/api/agentAuth';
import { useAgentStore } from '@/stores/agentStore';
import { ApiError } from '@/api/client';

export default function AgentLogin() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const setAuth = useAgentStore((s) => s.setAuth);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const res = await login({ username: username.trim(), password });
      setAuth(res.token, res.role, res.username);
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : '网络异常，请检查连接';
      console.error('[登录]', err);
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex-1 flex items-center justify-center bg-gray-50 dark:bg-gray-900">
      <form onSubmit={handleSubmit}
        className="bg-white dark:bg-gray-800 rounded-2xl shadow-lg p-8 w-full max-w-sm border border-gray-200 dark:border-gray-700">
        <div className="text-center mb-6">
          <div className="text-5xl mb-3">🎧</div>
          <h1 className="text-xl font-semibold text-gray-800 dark:text-gray-100">客服工作台</h1>
          <p className="text-sm text-gray-400 mt-1">请登录以管理工单</p>
        </div>
        {error && (
          <div className="mb-4 px-3 py-2 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-400 rounded-lg text-sm">
            {error}
          </div>
        )}
        <input type="text" value={username} onChange={e => setUsername(e.target.value)}
          placeholder="用户名"
          className="w-full px-4 py-3 mb-3 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50"
          disabled={loading} autoFocus />
        <input type="password" value={password} onChange={e => setPassword(e.target.value)}
          placeholder="密码"
          className="w-full px-4 py-3 mb-5 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50"
          disabled={loading} />
        <button type="submit" disabled={loading || !username.trim() || !password.trim()}
          className="w-full py-3 bg-primary-500 text-white rounded-xl font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors cursor-pointer disabled:cursor-not-allowed">
          {loading ? '登录中...' : '登录'}
        </button>
      </form>
    </div>
  );
}
