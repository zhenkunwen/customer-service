import { useState, useRef, useCallback, useEffect, forwardRef, type KeyboardEvent } from 'react';
import { useChat } from '@/hooks/useChat';
import { useUIStore } from '@/stores/uiStore';
import { useMessageStore } from '@/stores/messageStore';

const ChatInput = forwardRef<HTMLTextAreaElement>((_props, ref) => {
  const [text, setText] = useState('');
  const textRef = useRef<HTMLTextAreaElement | null>(null);
  const { send, stop, isRequesting } = useChat();
  const regenerateRequested = useUIStore((s) => s.regenerateRequested);
  const lastQuestion = useMessageStore((s) => s.lastQuestion);
  const clearRegenerate = useUIStore((s) => s.clearRegenerate);

  // 合并 forwarded ref 与内部 ref
  const setRefs = useCallback(
    (el: HTMLTextAreaElement | null) => {
      textRef.current = el;
      if (ref) {
        if (typeof ref === 'function') ref(el);
        else (ref as { current: HTMLTextAreaElement | null }).current = el;
      }
    },
    [ref],
  );

  // 监听重新生成请求
  useEffect(() => {
    if (regenerateRequested && lastQuestion && !isRequesting) {
      clearRegenerate();
      send(lastQuestion);
    }
  }, [regenerateRequested, lastQuestion, isRequesting, clearRegenerate, send]);

  // 自动调整输入框高度
  const autoResize = useCallback(() => {
    const el = textRef.current;
    if (!el) return;
    el.style.height = '0px';
    el.style.height = Math.min(el.scrollHeight, 240) + 'px';
  }, []);

  const handleSend = useCallback(() => {
    if (!text.trim() || isRequesting) return;
    send(text);
    setText('');
    requestAnimationFrame(autoResize);
  }, [text, isRequesting, send, autoResize]);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend],
  );

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      setText(e.target.value);
      requestAnimationFrame(autoResize);
    },
    [autoResize],
  );

  return (
    <div className="border-t border-gray-200 dark:border-gray-700 p-4 bg-white dark:bg-gray-800">
      <div className="flex items-end gap-2 max-w-3xl mx-auto">
        <textarea
          ref={setRefs}
          value={text}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          disabled={isRequesting}
          rows={2}
          placeholder="输入消息，Enter 发送，Shift+Enter 换行"
          className="flex-1 resize-none rounded-xl border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-900 px-4 py-3 text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50 transition-[height] duration-75"
        />
        {isRequesting ? (
          <button
            onClick={stop}
            className="px-4 py-2.5 rounded-xl bg-red-500 text-white text-sm font-medium hover:bg-red-600 transition-colors shrink-0"
          >
            ⏹ 停止
          </button>
        ) : (
          <button
            onClick={handleSend}
            disabled={!text.trim()}
            className="px-4 py-2.5 rounded-xl bg-primary-500 text-white text-sm font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors shrink-0"
          >
            发送
          </button>
        )}
      </div>
    </div>
  );
});

ChatInput.displayName = 'ChatInput';

export default ChatInput;
