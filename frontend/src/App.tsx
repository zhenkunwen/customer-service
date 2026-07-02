import { useEffect, useRef } from 'react';
import { useSessionStore } from '@/stores/sessionStore';
import { useMessageStore } from '@/stores/messageStore';
import { getSessionMessages } from '@/api/session';
import ConfigSidebar from '@/components/Sidebar/ConfigSidebar';
import MessageList from '@/components/Chat/MessageList';
import ChatInput from '@/components/Chat/ChatInput';
import { useUIStore } from '@/stores/uiStore';

export default function App() {
  const error = useUIStore((s) => s.error);
  const setError = useUIStore((s) => s.setError);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const sessionId = useSessionStore((s) => s.sessionId);
  const addAgentMessage = useMessageStore((s) => s.addAgentMessage);
  const clearMessages = useMessageStore((s) => s.clearMessages);
  const seenAgentIds = useRef<Set<number>>(new Set());

  // Poll agent messages every 10 seconds
  useEffect(() => {
    if (!sessionId) return;
    // 切换 session 时清除旧消息和已见记录
    seenAgentIds.current.clear();
    clearMessages();
    const poll = async () => {
      try {
        const records = await getSessionMessages(sessionId);
        if (!Array.isArray(records)) return;
        for (const r of records) {
          if (r.model === '__agent__' && r.id && !seenAgentIds.current.has(r.id)) {
            seenAgentIds.current.add(r.id);
            const sender = (r.userId || '').replace('agent:', '') || '客服';
            addAgentMessage(r.answer || '', sender);
          }
        }
      } catch { /* ignore polling errors */ }
    };
    poll(); // initial fetch
    const timer = setInterval(poll, 10000);
    return () => clearInterval(timer);
  }, [sessionId, addAgentMessage, clearMessages]);

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
    </div>
  );
}
