import { useEffect, useRef, useState, useCallback } from 'react';
import { useMessageStore } from '@/stores/messageStore';
import { useSessionStore } from '@/stores/sessionStore';
import { useUIStore } from '@/stores/uiStore';
import MessageBubble from './MessageBubble';
import LoadingDots from '../Common/LoadingDots';

export default function MessageList() {
  const messages = useMessageStore((s) => s.messages);
  const userId = useSessionStore((s) => s.userId);
  const { isRequesting, streamingMsgId } = useUIStore((s) => ({
    isRequesting: s.isRequesting,
    streamingMsgId: s.streamingMsgId,
  }));
  const containerRef = useRef<HTMLDivElement>(null);
  const [isNearBottom, setIsNearBottom] = useState(true);

  const handleScroll = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    setIsNearBottom(distFromBottom < 100);
  }, []);

  // Auto-scroll only when user is near the bottom
  useEffect(() => {
    if (isNearBottom) {
      const el = containerRef.current;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    }
  }, [messages, streamingMsgId, isNearBottom]);

  const scrollToBottom = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
    setIsNearBottom(true);
  }, []);

  // Find last assistant message index
  const lastAssistantIdx = useCallback(() => {
    for (let i = messages.length - 1; i >= 0; i--) {
      if (messages[i].role === 'assistant' && messages[i].id !== streamingMsgId) return i;
    }
    return -1;
  }, [messages, streamingMsgId]);

  if (messages.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400 dark:text-gray-500 text-sm">
        <div className="text-center">
          <div className="text-4xl mb-3">💬</div>
          <p>开始对话吧</p>
          <p className="text-xs mt-1">支持普通 / 流式 / 工具调用三种模式</p>
        </div>
      </div>
    );
  }

  const lastIdx = lastAssistantIdx();

  return (
    <div className="flex-1 relative min-h-0">
      <div
        ref={containerRef}
        className="absolute inset-0 overflow-y-auto py-4 space-y-3"
        role="log"
        aria-live="polite"
        aria-label="消息列表"
        onScroll={handleScroll}
      >
        {messages.map((m, i) => (
          <MessageBubble
            key={m.id}
            m={m}
            userId={userId}
            isStreaming={streamingMsgId === m.id}
            isLastAssistant={i === lastIdx}
          />
        ))}
        {isRequesting && !streamingMsgId && <LoadingDots />}
      </div>
      {!isNearBottom && (
        <button
          onClick={scrollToBottom}
          className="absolute bottom-4 left-1/2 -translate-x-1/2 px-3 py-1.5 rounded-full bg-primary-500 text-white text-xs font-medium shadow-lg hover:bg-primary-600 transition-colors z-10"
        >
          ↓ 新消息
        </button>
      )}
    </div>
  );
}
