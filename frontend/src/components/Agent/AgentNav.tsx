import { useAgentStore } from '@/stores/agentStore';

type View = 'tickets' | 'detail' | 'workspace' | 'admin';

interface AgentNavProps {
  currentView: View;
  onNavigate: (view: View) => void;
}

const MENU_ITEMS: { view: View; label: string; icon: string; roles: string[] }[] = [
  { view: 'tickets',   label: '工单管理', icon: '📋', roles: ['AGENT', 'TEAM_LEAD', 'ADMIN'] },
  { view: 'workspace', label: '客服工作台', icon: '👥', roles: ['TEAM_LEAD', 'ADMIN'] },
  { view: 'admin',     label: '管理面板', icon: '⚙️', roles: ['ADMIN'] },
];

export default function AgentNav({ currentView, onNavigate }: AgentNavProps) {
  const role = useAgentStore((s) => s.role);
  const visible = MENU_ITEMS.filter(m => m.roles.includes(role || ''));

  return (
    <nav className="w-48 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 p-3 flex flex-col gap-1 shrink-0">
      <div className="text-xs font-medium text-gray-400 uppercase tracking-wide px-3 py-2">客服后台</div>
      {visible.map(item => (
        <button key={item.view} onClick={() => onNavigate(item.view)}
          className={`flex items-center gap-2.5 px-3 py-2.5 rounded-lg text-sm transition-colors text-left ${
            currentView === item.view
              ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300 font-medium'
              : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'
          }`}>
          <span>{item.icon}</span>
          <span>{item.label}</span>
        </button>
      ))}
      <div className="flex-1" />
      <div className="text-xs text-gray-400 px-3 py-2 border-t border-gray-100 dark:border-gray-700 pt-3">
        {role === 'ADMIN' ? '管理员' : role === 'TEAM_LEAD' ? '主管' : '客服'}
      </div>
    </nav>
  );
}
