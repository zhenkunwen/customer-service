import { useRef, useCallback } from 'react';
import { postStreamChat } from '@/api/stream';
import type { ChatRequest, StreamEvent } from '@/api/types';

export function useStream() {
  const abortRef = useRef<AbortController | null>(null);

  const start = useCallback(
    async (
      req: ChatRequest,
      onToken: (token: string) => void,
      onDone: () => void,
      onError: (err: string) => void,
    ) => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      try {
        for await (const event of postStreamChat(req, controller.signal)) {
          if (controller.signal.aborted) break;
          if (event.event === 'token') {
            onToken(event.data);
          } else if (event.event === 'done') {
            onDone();
            break;
          } else if (event.event === 'error') {
            onError(event.data);
            break;
          }
        }
      } catch (e: unknown) {
        if (!controller.signal.aborted) {
          const msg = e instanceof Error ? e.message : '流式连接中断';
          onError(msg);
        }
      }
    },
    [],
  );

  const abort = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
  }, []);

  return { start, abort };
}
