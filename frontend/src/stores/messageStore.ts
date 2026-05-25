import { create } from 'zustand';
import type { Message } from '@/api/types';
import { v4 as uuidv4 } from 'uuid';

interface MessageStore {
  messages: Message[];
  lastQuestion: string;
  addUserMessage: (content: string) => Message;
  addAssistantMessage: (content: string, model?: string, latencyMs?: number, fallback?: boolean) => Message;
  addToolMessage: (toolCalls: Message['toolCalls']) => void;
  appendStreamToken: (msgId: string, token: string) => void;
  clearMessages: () => void;
}

export const useMessageStore = create<MessageStore>((set, get) => ({
  messages: [],
  lastQuestion: '',

  addUserMessage: (content) => {
    const msg: Message = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: Date.now(),
    };
    set((s) => ({ messages: [...s.messages, msg], lastQuestion: content }));
    return msg;
  },

  addAssistantMessage: (content, model, latencyMs, fallback) => {
    const msg: Message = {
      id: uuidv4(),
      role: 'assistant',
      content,
      model,
      latencyMs,
      fallback,
      timestamp: Date.now(),
    };
    set((s) => ({ messages: [...s.messages, msg] }));
    return msg;
  },

  addToolMessage: (toolCalls) => {
    const msg: Message = {
      id: uuidv4(),
      role: 'tool',
      content: '',
      toolCalls: toolCalls ?? undefined,
      timestamp: Date.now(),
    };
    set((s) => ({ messages: [...s.messages, msg] }));
  },

  appendStreamToken: (msgId, token) => {
    set((s) => ({
      messages: s.messages.map((m) =>
        m.id === msgId ? { ...m, content: m.content + token } : m,
      ),
    }));
  },

  clearMessages: () => set({ messages: [], lastQuestion: '' }),
}));
