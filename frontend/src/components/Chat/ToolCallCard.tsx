import { useState } from 'react';
import type { ToolCallRecord } from '@/api/types';

export default function ToolCallCard({ tc }: { tc: ToolCallRecord }) {
  const [open, setOpen] = useState(false);

  let args: Record<string, unknown> = {};
  try { args = JSON.parse(tc.arguments); } catch { /* keep raw */ }

  let result: Record<string, unknown> | string = tc.result;
  try { result = JSON.parse(tc.result); } catch { /* keep raw */ }

  const isError = typeof result === 'object' && result !== null && 'error' in result;

  return (
    <div className={`my-2 border rounded-lg overflow-hidden ${
      isError ? 'border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-900/20' :
      'border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800'
    }`}>
      <button
        onClick={() => setOpen(!open)}
        className="w-full px-3 py-2 flex items-center justify-between text-sm font-medium hover:opacity-80"
      >
        <span>🔧 {tc.toolName}</span>
        <span className="text-xs text-gray-400">{open ? '收起 ▲' : '展开 ▼'}</span>
      </button>
      {open && (
        <div className="px-3 pb-3 space-y-2 text-xs font-mono">
          <div>
            <div className="text-gray-500 dark:text-gray-400 mb-1">参数</div>
            <pre className="bg-white dark:bg-gray-900 p-2 rounded border border-gray-200 dark:border-gray-700 overflow-x-auto whitespace-pre-wrap">
              {JSON.stringify(args, null, 2) || tc.arguments}
            </pre>
          </div>
          <div>
            <div className="text-gray-500 dark:text-gray-400 mb-1">结果</div>
            <pre className={`p-2 rounded border overflow-x-auto whitespace-pre-wrap ${
              isError ? 'bg-red-100 dark:bg-red-900/30 border-red-300 dark:border-red-700 text-red-700 dark:text-red-300' :
              'bg-white dark:bg-gray-900 border-gray-200 dark:border-gray-700'
            }`}>
              {typeof result === 'string' ? result : JSON.stringify(result, null, 2)}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
}
