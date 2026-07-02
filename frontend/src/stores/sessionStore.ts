import { create } from 'zustand';
import type { SessionConfig, TenantId } from '@/api/types';
import { v4 as uuidv4 } from 'uuid';
import { useMessageStore } from './messageStore';

function loadSessionId(): string {
  try {
    const saved = localStorage.getItem('cs-session-id');
    return saved || uuidv4();
  } catch {
    return uuidv4();
  }
}

function persistSessionId(id: string) {
  try {
    localStorage.setItem('cs-session-id', id);
  } catch { /* ignore */ }
}

const defaultApiKeys: Record<string, string> = {
  default: 'change-me',
  'tenant-a': 'change-me',
  'tenant-b': 'change-me',
};

function loadApiKey(tenantId: string): string {
  try {
    const saved = localStorage.getItem(`cs-apikey-${tenantId}`);
    return saved || defaultApiKeys[tenantId] || '';
  } catch {
    return defaultApiKeys[tenantId] || '';
  }
}

interface SessionStore extends SessionConfig {
  apiKey: string;
  setTenant: (tenantId: TenantId) => void;
  setUserId: (userId: string) => void;
  setMode: (mode: SessionConfig['mode']) => void;
  setApiKey: (key: string) => void;
  newSession: () => void;
}

export const useSessionStore = create<SessionStore>((set, get) => ({
  sessionId: loadSessionId(),
  tenantId: 'default',
  userId: 'user-001',
  mode: 'normal',
  apiKey: loadApiKey('default'),

  setTenant: (tenantId) => {
    try { localStorage.setItem('cs-tenant-id', tenantId); } catch { /* ignore */ }
    set({ tenantId, apiKey: loadApiKey(tenantId) });
  },

  setUserId: (userId) => set({ userId }),

  setMode: (mode) => set({ mode }),

  setApiKey: (apiKey) => {
    const { tenantId } = get();
    try { localStorage.setItem(`cs-apikey-${tenantId}`, apiKey); } catch { /* ignore */ }
    set({ apiKey });
  },

  newSession: () => {
    const newId = uuidv4();
    persistSessionId(newId);
    useMessageStore.getState().clearMessages();
    set({ sessionId: newId });
  },
}));
