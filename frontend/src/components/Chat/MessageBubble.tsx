import { useState, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Message } from '@/api/types';
import { useUIStore } from '@/stores/uiStore';
import ToolCallCard from './ToolCallCard';

function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

function Avatar({ role }: { role: 'user' | 'assistant' | 'agent' }) {
  if (role === 'assistant') {
    return (
      <img
        src="/assets/cs-avatar.png"
        alt="AI"
        className="w-8 h-8 rounded-full shrink-0 shadow-sm object-cover"
      />
    );
  }
  if (role === 'agent') {
    return (
      <div className="w-8 h-8 rounded-full shrink-0 shadow-sm bg-blue-100 dark:bg-blue-900 flex items-center justify-center text-sm">
        👤
      </div>
    );
  }
  return (
    <img
      src="/assets/user-avatar.png"
      alt="用户"
      className="w-8 h-8 rounded-full shrink-0 shadow-sm object-cover"
    />
  );
}

export default function MessageBubble({
  m,
  isStreaming,
  isLastAssistant,
  userId,
}: {
  m: Message;
  isStreaming?: boolean;
  isLastAssistant?: boolean;
  userId?: string;
}) {
  const isUser = m.role === 'user';
  const isTool = m.role === 'tool';
  const isAssistant = m.role === 'assistant';
  const isAgent = m.role === 'agent';
  const [copied, setCopied] = useState(false);
  const [thumb, setThumb] = useState<'up' | 'down' | null>(null);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(m.content).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [m.content]);

  const handleRegenerate = useCallback(() => {
    useUIStore.getState().requestRegenerate();
  }, []);

  if (isTool && m.toolCalls) {
    return (
      <div className="px-4">
        <div className="text-xs text-gray-400 dark:text-gray-500 mb-1">{formatTime(m.timestamp)}</div>
        {m.toolCalls.map((tc, i) => (
          <ToolCallCard key={i} tc={tc} />
        ))}
      </div>
    );
  }

  return (
    <div className={`flex flex-col px-4 group ${isUser ? 'items-end' : 'items-start'}`}>
      {/* 头像 + 消息行 */}
      <div className={`flex items-end gap-2 max-w-full ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
        <Avatar role={isAssistant ? 'assistant' : isAgent ? 'agent' : 'user'} />
        <div
          className={`max-w-[70%] rounded-2xl px-4 py-2.5 text-sm ${
            isUser
              ? 'bg-primary-500 text-white rounded-br-md'
              : isAgent
                ? 'bg-blue-50 dark:bg-blue-900/20 text-gray-800 dark:text-gray-200 border border-blue-200 dark:border-blue-800 rounded-bl-md shadow-sm'
                : isAssistant
                  ? 'bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-100 border border-gray-200 dark:border-gray-700 rounded-bl-md shadow-sm'
                  : 'bg-yellow-50 dark:bg-yellow-900/20 text-gray-600 dark:text-gray-400 border border-yellow-200 dark:border-yellow-800'
          }`}
        >
          {isAssistant && m.model && (
            <div className="text-xs text-gray-400 dark:text-gray-500 mb-1 flex items-center gap-2">
              <span>{m.model}</span>
              {m.latencyMs != null && m.latencyMs > 0 && <span>· {m.latencyMs}ms</span>}
            </div>
          )}
          {isAgent && (
            <div className="text-xs text-blue-500 dark:text-blue-400 mb-1 flex items-center gap-2">
              <span>👤 客服 · {m.model}</span>
            </div>
          )}
          <div className="break-words">
            {isAssistant ? (
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  a: ({ children, ...props }) => (
                    <a
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-primary-600 dark:text-primary-400 underline"
                      {...props}
                    >
                      {children}
                    </a>
                  ),
                  pre: ({ children }) => <>{children}</>,
                  code: ({ children, className, ...props }) => {
                    if (!className) {
                      return (
                        <code
                          className="bg-gray-100 dark:bg-gray-700 px-1 py-0.5 rounded text-sm"
                          {...props}
                        >
                          {children}
                        </code>
                      );
                    }
                    return (
                      <pre className="bg-gray-100 dark:bg-gray-700 p-3 rounded-lg overflow-x-auto my-2 text-sm">
                        <code className={className} {...props}>
                          {children}
                        </code>
                      </pre>
                    );
                  },
                }}
              >
                {m.content}
              </ReactMarkdown>
            ) : (
              <span className="whitespace-pre-wrap">{m.content}</span>
            )}
            {isStreaming && (
              <span className="inline-block w-0.5 h-4 bg-primary-500 animate-pulse align-middle ml-0.5" />
            )}
          </div>
          {m.fallback && (
            <div className="mt-1 text-xs bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400 px-2 py-0.5 rounded">
              ⚠ 降级响应
            </div>
          )}
        </div>
      </div>

      {/* 操作按钮 + 时间戳 */}
      <div className={`flex items-center gap-2 mt-0.5 ${isUser ? 'mr-10 flex-row-reverse' : 'ml-10'}`}>
        <span className="text-[10px] text-gray-400 dark:text-gray-500">
          {formatTime(m.timestamp)}
        </span>
        {isAssistant && !isStreaming && (
          <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
            <button
              onClick={handleCopy}
              className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
              title={copied ? '已复制' : '复制'}
            >
              {copied ? '✓' : '📋'}
            </button>
            <button
              onClick={() => setThumb(thumb === 'up' ? null : 'up')}
              className={`p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-xs ${
                thumb === 'up' ? 'text-blue-500' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-300'
              }`}
              title="赞"
            >
              👍
            </button>
            <button
              onClick={() => setThumb(thumb === 'down' ? null : 'down')}
              className={`p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-xs ${
                thumb === 'down' ? 'text-red-500' : 'text-gray-400 hover:text-gray-600 dark:hover:text-gray-300'
              }`}
              title="踩"
            >
              👎
            </button>
            {isLastAssistant && (
              <button
                onClick={handleRegenerate}
                className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                title="重新生成"
              >
                🔄
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
