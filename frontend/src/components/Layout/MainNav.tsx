interface MainNavProps {
  mode: 'chat' | 'agent';
  onModeChange: (mode: 'chat' | 'agent') => void;
}

export default function MainNav({ mode, onModeChange }: MainNavProps) {
  return (
    <nav className="w-14 flex flex-col items-center py-3 gap-2 bg-gray-100 dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 shrink-0">
      <button onClick={() => onModeChange('chat')}
        className={`w-10 h-10 rounded-xl flex items-center justify-center text-lg transition-colors ${
          mode === 'chat'
            ? 'bg-primary-500 text-white shadow-sm'
            : 'text-gray-400 dark:text-gray-500 hover:bg-gray-200 dark:hover:bg-gray-700'
        }`} title="客户聊天">
        💬
      </button>
      <button onClick={() => onModeChange('agent')}
        className={`w-10 h-10 rounded-xl flex items-center justify-center text-lg transition-colors ${
          mode === 'agent'
            ? 'bg-primary-500 text-white shadow-sm'
            : 'text-gray-400 dark:text-gray-500 hover:bg-gray-200 dark:hover:bg-gray-700'
        }`} title="客服工作台">
        🎧
      </button>
    </nav>
  );
}
