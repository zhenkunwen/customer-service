import { useState, useEffect, useCallback } from 'react';
import axios from 'axios';

interface HealthStatus {
  status: 'UP' | 'DOWN';
  components?: Record<string, { status: string }>;
}

export function useHealth() {
  const [status, setStatus] = useState<'UP' | 'DOWN' | 'checking'>('checking');
  const [details, setDetails] = useState<Record<string, string>>({});

  const check = useCallback(async () => {
    try {
      const { data } = await axios.get<HealthStatus>('/actuator/health');
      setStatus(data.status);
      if (data.components) {
        const map: Record<string, string> = {};
        for (const [key, val] of Object.entries(data.components)) {
          map[key] = val.status;
        }
        setDetails(map);
      }
    } catch {
      setStatus('DOWN');
      setDetails({});
      console.warn('[Health] 后端健康检查失败');
    }
  }, []);

  useEffect(() => {
    check();
    const timer = setInterval(check, 30000);
    return () => clearInterval(timer);
  }, [check]);

  return { status, details };
}
