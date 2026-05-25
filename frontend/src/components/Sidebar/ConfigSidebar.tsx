import { useEffect, useState } from 'react';
import type { TenantId } from '@/api/types';
import { useSessionStore } from '@/stores/sessionStore';
import { useMessageStore } from '@/stores/messageStore';
import { useUIStore } from '@/stores/uiStore';
import { useHealth } from '@/hooks/useHealth';

const tenants: { id: TenantId; label: string }[] = [
  { id: 'default', label: '默认租户' },
  { id: 'tenant-a', label: '租户 A' },
  { id: 'tenant-b', label: '租户 B' },
];

const tenantParams: Record<TenantId, { model: string; temperature: number; maxTokens: number }> = {
  default: { model: 'deepseek-chat', temperature: 0.7, maxTokens: 2048 },
  'tenant-a': { model: 'deepseek-chat', temperature: 0.5, maxTokens: 1024 },
  'tenant-b': { model: 'deepseek-chat', temperature: 0.8, maxTokens: 4096 },
};

const modes = [
  { id: 'normal' as const, label: '普通', icon: '💬' },
  { id: 'stream' as const, label: '流式', icon: '⚡' },
  { id: 'tool' as const, label: '工具', icon: '🔧' },
];

const toolDescriptions: Record<string, string> = {
  orderTool: '根据 userId + orderId 查询订单状态、金额和详情',
  logisticsTool: '根据 orderId 查询快递轨迹，含承运商和运单号',
  refundTool: '根据 productType 查询退货政策（天数、条件）',
};

const toolTestHints: Record<string, string> = {
  orderTool: '试试说："帮我查订单 ORD-20240001"',
  logisticsTool: '试试说："查物流 ORD-20240001"',
  refundTool: '试试说："电子产品怎么退货？"',
};

function loadDarkMode(): boolean {
  try {
    const saved = localStorage.getItem('cs-dark-mode');
    if (saved !== null) return saved === 'true';
  } catch { /* ignore */ }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
}

function persistDarkMode(dark: boolean) {
  try { localStorage.setItem('cs-dark-mode', String(dark)); } catch { /* ignore */ }
}

export default function ConfigSidebar() {
  const { tenantId, userId, sessionId, mode, apiKey, setTenant, setUserId, setMode, setApiKey, newSession } =
    useSessionStore();
  const clearMessages = useMessageStore((s) => s.clearMessages);
  const { isRequesting, stopStreaming } = useUIStore((s) => ({
    isRequesting: s.isRequesting,
    stopStreaming: s.stopStreaming,
  }));
  const { status, details } = useHealth();

  const [dark, setDark] = useState(loadDarkMode);
  const [showKey, setShowKey] = useState(false);
  const [confirmNew, setConfirmNew] = useState(false);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
    persistDarkMode(dark);
  }, [dark]);

  const handleNewSession = () => {
    if (messages.length === 0) {
      doNewSession();
      return;
    }
    setConfirmNew(true);
  };

  const doNewSession = () => {
    if (isRequesting) stopStreaming();
    newSession();
    clearMessages();
    setConfirmNew(false);
  };

  const messages = useMessageStore((s) => s.messages);
  const tp = tenantParams[tenantId as TenantId];

  return (
    <aside className="w-full lg:w-72 bg-white dark:bg-gray-800 border-b lg:border-b-0 lg:border-r border-gray-200 dark:border-gray-700 p-4 flex flex-col gap-3 shrink-0 overflow-y-auto">
      {/* 头部 */}
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-bold text-gray-800 dark:text-gray-100 flex items-center gap-2">
          🤖 智能客服
        </h1>
        <button
          onClick={() => setDark(!dark)}
          className="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-lg"
          title={dark ? '切换浅色' : '切换深色'}
          aria-label={dark ? '切换浅色模式' : '切换深色模式'}
        >
          {dark ? '☀️' : '🌙'}
        </button>
      </div>

      {/* 健康状态 */}
      <div className="flex items-center gap-2 text-xs" role="status" aria-label="服务健康状态">
        <span className={`w-2 h-2 rounded-full ${
          status === 'UP' ? 'bg-green-500' : status === 'checking' ? 'bg-yellow-400 animate-pulse' : 'bg-red-500'
        }`} />
        <span className="text-gray-500 dark:text-gray-400">
          {status === 'UP' ? '服务正常' : status === 'checking' ? '检测中...' : '服务异常'}
        </span>
        {status === 'UP' && details.db && (
          <span className="text-gray-400 dark:text-gray-500">
            DB:{details.db === 'UP' ? '✓' : '✗'} Redis:{details.redis === 'UP' ? '✓' : '✗'}
          </span>
        )}
      </div>

      {/* 租户选择 */}
      <div>
        <label className="text-xs text-gray-500 dark:text-gray-400 mb-1 block" id="tenant-label">租户</label>
        <select
          value={tenantId}
          onChange={(e) => setTenant(e.target.value as TenantId)}
          className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 px-3 py-2 text-sm"
          aria-labelledby="tenant-label"
        >
          {tenants.map((t) => (
            <option key={t.id} value={t.id}>{t.label}</option>
          ))}
        </select>
        <div className="mt-1 text-xs text-gray-400 dark:text-gray-500 space-y-0.5">
          <span>模型 {tp.model} · 温度 {tp.temperature} · {tp.maxTokens} tokens</span>
        </div>
      </div>

      {/* 用户ID */}
      <div>
        <label className="text-xs text-gray-500 dark:text-gray-400 mb-1 block" id="userid-label">用户 ID</label>
        <input
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 px-3 py-2 text-sm"
          placeholder="输入用户ID"
          aria-labelledby="userid-label"
        />
      </div>

      {/* API Key */}
      <div>
        <label className="text-xs text-gray-500 dark:text-gray-400 mb-1 block" id="apikey-label">API Key</label>
        <div className="relative">
          <input
            type={showKey ? 'text' : 'password'}
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 px-3 py-2 pr-9 text-sm font-mono"
            placeholder="输入 X-API-Key"
            aria-labelledby="apikey-label"
          />
          <button
            type="button"
            onClick={() => setShowKey(!showKey)}
            className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 text-sm"
            title={showKey ? '隐藏' : '显示'}
            aria-label={showKey ? '隐藏 API Key' : '显示 API Key'}
          >
            {showKey ? '🙈' : '👁'}
          </button>
        </div>
      </div>

      {/* 模式切换 */}
      <div>
        <label className="text-xs text-gray-500 dark:text-gray-400 mb-1 block" id="mode-label">对话模式</label>
        <div className="flex gap-1" role="radiogroup" aria-labelledby="mode-label">
          {modes.map((m) => (
            <button
              key={m.id}
              onClick={() => { if (!isRequesting) setMode(m.id); }}
              disabled={isRequesting}
              role="radio"
              aria-checked={mode === m.id}
              className={`flex-1 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                mode === m.id
                  ? 'bg-primary-500 text-white'
                  : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200'
              } disabled:opacity-50`}
            >
              {m.icon} {m.label}
            </button>
          ))}
        </div>
      </div>

      {/* 工具说明面板 — 仅工具模式 */}
      {mode === 'tool' && (
        <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3 space-y-2">
          <div className="text-xs font-bold text-blue-700 dark:text-blue-300">可用工具</div>
          {Object.entries(toolDescriptions).map(([name, desc]) => (
            <div key={name} className="text-xs">
              <span className="font-mono font-bold text-blue-600 dark:text-blue-400">{name}</span>
              <p className="text-gray-500 dark:text-gray-400 mt-0.5">{desc}</p>
              <p className="text-blue-400 dark:text-blue-500 mt-0.5 italic">{toolTestHints[name]}</p>
            </div>
          ))}
        </div>
      )}

      {/* 会话信息 */}
      <div className="bg-gray-50 dark:bg-gray-900 rounded-lg p-3 space-y-1 text-xs">
        <div className="text-gray-500 dark:text-gray-400">
          会话 <span className="font-mono text-gray-700 dark:text-gray-300">{sessionId.slice(0, 8)}...</span>
        </div>
        {messages.length > 0 && (
          <div className="text-gray-400 dark:text-gray-500">共 {messages.length} 条消息</div>
        )}
      </div>

      {/* 新建会话 */}
      {confirmNew ? (
        <div className="space-y-2">
          <p className="text-xs text-gray-500 dark:text-gray-400">确认清空当前对话？</p>
          <div className="flex gap-2">
            <button
              onClick={doNewSession}
              className="flex-1 py-2 rounded-lg bg-red-500 text-white text-sm font-medium hover:bg-red-600 transition-colors"
            >
              确认
            </button>
            <button
              onClick={() => setConfirmNew(false)}
              className="flex-1 py-2 rounded-lg border border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-400 text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
            >
              取消
            </button>
          </div>
        </div>
      ) : (
        <button
          onClick={handleNewSession}
          className="w-full py-2 rounded-lg border border-red-300 dark:border-red-700 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/30 text-sm font-medium transition-colors"
          aria-label="新建会话"
        >
          🔄 新建会话
        </button>
      )}
    </aside>
  );
}
