import { useState, useEffect } from 'react';
import { registerAgent, getDegradationStatus, toggleDegradation } from '@/api/agentAuth';
import { ApiError } from '@/api/client';
import AgentWorkspace from './AgentWorkspace';

function SettingsPanel() {
  const [enabled, setEnabled] = useState(false);
  const [loading, setLoading] = useState(true);
  const [toggling, setToggling] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getDegradationStatus()
      .then(d => { setEnabled(d.enabled); setLoading(false); })
      .catch(() => { setLoading(false); });
  }, []);

  const handleToggle = async () => {
    setToggling(true);
    setError(null);
    try { const d = await toggleDegradation(); setEnabled(d.enabled); }
    catch (err) { setError(err instanceof ApiError ? err.message : '操作失败'); }
    finally { setToggling(false); }
  };

  return (
    <div className="space-y-4">
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
        <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">🔧 系统降级模式</h3>
        <p className="text-xs text-gray-400 mb-3">开启后 AI 对话将使用降级回复，不调用模型</p>
        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-500">
            状态: {loading ? <span className="text-gray-400">加载中...</span> : (
              <span className={enabled ? 'text-red-600 font-medium' : 'text-green-600 font-medium'}>{enabled ? '已开启' : '已关闭'}</span>
            )}
          </span>
          <button onClick={handleToggle} disabled={loading || toggling}
            className="px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 transition-colors cursor-pointer">
            {toggling ? '切换中...' : '切换'}
          </button>
        </div>
        {error && <p className="text-xs text-red-500 mt-2">{error}</p>}
      </div>
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
        <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">🔄 后端健康状态</h3>
        <p className="text-xs text-gray-400">系统各组件状态见 /actuator/health</p>
      </div>
    </div>
  );
}

type AdminTab = 'register' | 'agents' | 'settings';

export default function AdminPanel() {
  const [tab, setTab] = useState<AdminTab>('register');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('AGENT');
  const [registering, setRegistering] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) return;
    setRegistering(true);
    setMessage(null);
    try {
      const res = await registerAgent(username.trim(), password, role);
      setMessage({ type: 'success', text: `客服「${res.username}」注册成功！` });
      setUsername(''); setPassword(''); setRole('AGENT');
    } catch (err) {
      setMessage({ type: 'error', text: err instanceof ApiError ? err.message : '注册失败' });
    } finally { setRegistering(false); }
  };

  const TABS: { key: AdminTab; label: string }[] = [
    { key: 'register', label: '注册新客服' },
    { key: 'agents', label: '客服列表' },
    { key: 'settings', label: '系统设置' },
  ];

  return (
    <div className="p-6">
      <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">⚙️ 管理面板</h2>
      <div className="flex gap-1 mb-6 border-b border-gray-200 dark:border-gray-700">
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`px-4 py-2.5 text-sm rounded-t-lg border border-b-0 transition-colors ${
              tab === t.key
                ? 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700 text-primary-600 dark:text-primary-400 font-medium -mb-px'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
            }`}>{t.label}</button>
        ))}
      </div>

      {tab === 'register' && (
        <form onSubmit={handleRegister} className="max-w-md">
          {message && (
            <div className={`mb-4 px-3 py-2 rounded-lg text-sm border ${
              message.type === 'success'
                ? 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800 text-green-600 dark:text-green-400'
                : 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800 text-red-600 dark:text-red-400'
            }`}>{message.text}</div>
          )}
          <input type="text" value={username} onChange={e => setUsername(e.target.value)}
            placeholder="用户名" disabled={registering}
            className="w-full px-4 py-3 mb-3 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50" />
          <input type="password" value={password} onChange={e => setPassword(e.target.value)}
            placeholder="密码" disabled={registering}
            className="w-full px-4 py-3 mb-3 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50" />
          <select value={role} onChange={e => setRole(e.target.value)} disabled={registering}
            className="w-full px-4 py-3 mb-4 border border-gray-300 dark:border-gray-600 rounded-xl bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50">
            <option value="AGENT">客服 (AGENT)</option>
            <option value="TEAM_LEAD">主管 (TEAM_LEAD)</option>
          </select>
          <button type="submit" disabled={registering || !username.trim() || !password.trim()}
            className="px-6 py-3 bg-primary-500 text-white rounded-xl font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors cursor-pointer">
            {registering ? '注册中...' : '注册新客服'}
          </button>
        </form>
      )}

      {tab === 'agents' && <AgentWorkspace />}
      {tab === 'settings' && (
        <div className="max-w-md space-y-4"><SettingsPanel /></div>
      )}
    </div>
  );
}
