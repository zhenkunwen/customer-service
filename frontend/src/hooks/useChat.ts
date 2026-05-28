import { useCallback, useRef } from 'react';
import { useShallow } from 'zustand/react/shallow';
import { useSessionStore } from '@/stores/sessionStore';
import { useMessageStore } from '@/stores/messageStore';
import { useUIStore } from '@/stores/uiStore';
import { postNormalChat, postToolChat } from '@/api/chat';
import { useStream } from './useStream';
import type { ChatRequest } from '@/api/types';

export function useChat() {
  const abortRef = useRef<AbortController | null>(null);

  const { sessionId, tenantId, userId, mode } = useSessionStore(
    useShallow((s) => ({
      sessionId: s.sessionId,
      tenantId: s.tenantId,
      userId: s.userId,
      mode: s.mode,
    })),
  );

  const { addUserMessage, addAssistantMessage, addToolMessage, appendStreamToken } =
    useMessageStore(
      useShallow((s) => ({
        addUserMessage: s.addUserMessage,
        addAssistantMessage: s.addAssistantMessage,
        addToolMessage: s.addToolMessage,
        appendStreamToken: s.appendStreamToken,
      })),
    );

  const { isRequesting, setRequesting, startStreaming, stopStreaming, setError } =
    useUIStore(
      useShallow((s) => ({
        isRequesting: s.isRequesting,
        setRequesting: s.setRequesting,
        startStreaming: s.startStreaming,
        stopStreaming: s.stopStreaming,
        setError: s.setError,
      })),
    );

  const { start: startStream, abort: abortStream } = useStream();

  const buildReq = useCallback(
    (question: string): ChatRequest => ({
      sessionId,
      tenantId,
      userId,
      question,
      streamMode: mode === 'stream',
      toolMode: mode === 'tool',
    }),
    [sessionId, tenantId, userId, mode],
  );

  const send = useCallback(
    async (question: string) => {
      if (isRequesting || !question.trim()) return;
      setError(null);
      addUserMessage(question.trim());
      setRequesting(true);

      const req = buildReq(question.trim());

      if (mode === 'stream') {
        const streamMsg = addAssistantMessage('');
        startStreaming(streamMsg.id);
        try {
          await startStream(
            req,
            (token) => appendStreamToken(streamMsg.id, token),
            () => stopStreaming(),
            (err) => {
              stopStreaming();
              setError(err);
            },
          );
        } finally {
          setRequesting(false);
        }
      } else {
        const controller = new AbortController();
        abortRef.current = controller;

        try {
          const res = await (mode === 'tool'
            ? postToolChat(req, controller.signal)
            : postNormalChat(req, controller.signal));

          if (res.toolCalls && res.toolCalls.length > 0) {
            addToolMessage(res.toolCalls);
          }
          addAssistantMessage(res.answer, res.model, res.latencyMs, res.fallback);
        } catch (e: unknown) {
          if (controller.signal.aborted) return; // 用户主动取消，不报错
          const msg = e instanceof Error ? e.message : '请求失败';
          setError(msg);
        } finally {
          setRequesting(false);
          abortRef.current = null;
        }
      }
    },
    [
      isRequesting,
      mode,
      buildReq,
      addUserMessage,
      addAssistantMessage,
      addToolMessage,
      appendStreamToken,
      setRequesting,
      startStreaming,
      stopStreaming,
      setError,
      startStream,
    ],
  );

  const stop = useCallback(() => {
    abortRef.current?.abort();
    abortStream();
    setRequesting(false);
    stopStreaming();
  }, [abortStream, setRequesting, stopStreaming]);

  return { send, stop, isRequesting };
}
