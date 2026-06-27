import { useEffect, useRef, useState } from 'react';
import ConfigSidebar from '@/components/Sidebar/ConfigSidebar';
import MessageList from '@/components/Chat/MessageList';
import ChatInput from '@/components/Chat/ChatInput';
import MainNav from '@/components/Layout/MainNav';
import AgentLogin from '@/pages/AgentLogin';
import AgentDashboard from '@/pages/AgentDashboard';
import { useUIStore } from '@/stores/uiStore';
import { useAgentStore } from '@/stores/agentStore';

export default function App() {
  const [mode, setMode] = useState<'chat' | 'agent'>('chat');
  const agentToken = useAgentStore((s) => s.token);
  const error = useUIStore((s) => s.error);
  const setError = useUIStore((s) => s.setError);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (error) {
      const timer = setTimeout(() => setError(null), 5000);
      return () => clearTimeout(timer);
    }
  }, [error, setError]);

  // 全局快捷键 Ctrl+K / Cmd+K 聚焦输入框
  useEffect(() => {
    const handler = (e: globalThis.KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName;
      // 不在输入框中时才触发
      if (tag !== 'INPUT' && tag !== 'TEXTAREA' && !(e.ctrlKey || e.metaKey) && e.key === '/') {
        e.preventDefault();
        inputRef.current?.focus();
        return;
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        inputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  return (
    <div className="h-screen flex bg-gray-50 dark:bg-gray-900">
      <MainNav mode={mode} onModeChange={setMode} />
      {mode === 'chat' ? (
        <div className="flex-1 flex flex-col lg:flex-row min-w-0">
          <ConfigSidebar />
          <main className="flex-1 flex flex-col min-h-0">
            {error && (
              <div className="mx-4 mt-2 px-4 py-2 bg-red-100 dark:bg-red-900/30 border border-red-300 dark:border-red-700 text-red-700 dark:text-red-300 rounded-lg text-sm flex items-center justify-between">
                <span>❌ {error}</span>
                <button onClick={() => setError(null)} className="ml-2 font-bold">×</button>
              </div>
            )}
            <MessageList />
            <ChatInput ref={inputRef} />
          </main>
        </div>
      ) : agentToken ? (
        <AgentDashboard />
      ) : (
        <AgentLogin />
      )}
    </div>
  );
}
