import { create } from 'zustand';

interface AgentStore {
  token: string | null;
  role: string | null;
  username: string | null;
  setAuth: (token: string, role: string, username: string) => void;
  clearAuth: () => void;
}

function load(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}

export const useAgentStore = create<AgentStore>((set) => ({
  token: load('cs-agent-token'),
  role: load('cs-agent-role'),
  username: load('cs-agent-username'),

  setAuth: (token, role, username) => {
    try {
      localStorage.setItem('cs-agent-token', token);
      localStorage.setItem('cs-agent-role', role);
      localStorage.setItem('cs-agent-username', username);
    } catch { /* ignore */ }
    set({ token, role, username });
  },

  clearAuth: () => {
    try {
      localStorage.removeItem('cs-agent-token');
      localStorage.removeItem('cs-agent-role');
      localStorage.removeItem('cs-agent-username');
    } catch { /* ignore */ }
    set({ token: null, role: null, username: null });
  },
}));
