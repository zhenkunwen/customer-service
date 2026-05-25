import { create } from 'zustand';

interface UIStore {
  isRequesting: boolean;
  streamingMsgId: string | null;
  error: string | null;
  regenerateRequested: boolean;
  setRequesting: (v: boolean) => void;
  startStreaming: (msgId: string) => void;
  stopStreaming: () => void;
  setError: (e: string | null) => void;
  requestRegenerate: () => void;
  clearRegenerate: () => void;
}

export const useUIStore = create<UIStore>((set) => ({
  isRequesting: false,
  streamingMsgId: null,
  error: null,
  regenerateRequested: false,

  setRequesting: (v) => set({ isRequesting: v }),

  startStreaming: (msgId) => set({ streamingMsgId: msgId, isRequesting: true }),

  stopStreaming: () => set({ streamingMsgId: null, isRequesting: false }),

  setError: (e) => set({ error: e }),

  requestRegenerate: () => set({ regenerateRequested: true }),

  clearRegenerate: () => set({ regenerateRequested: false }),
}));
