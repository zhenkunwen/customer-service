import axios, { AxiosError } from 'axios';
import { ApiError } from './client';

const agentClient = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

agentClient.interceptors.request.use((config) => {
  try {
    const token = localStorage.getItem('cs-agent-token');
    if (token) config.headers['X-Agent-Token'] = token;
  } catch { /* ignore */ }
  return config;
});

agentClient.interceptors.response.use(
  (res) => res,
  (error: AxiosError) => {
    if (axios.isCancel(error)) return Promise.reject(new ApiError('请求已取消', 0));
    if (error.code === 'ECONNABORTED') return Promise.reject(new ApiError('请求超时，请稍后重试', 408));
    if (!error.response) return Promise.reject(new ApiError('网络异常，请检查连接', 0));
    const { status, data } = error.response;
    if (status === 401) {
      localStorage.removeItem('cs-agent-token');
      localStorage.removeItem('cs-agent-role');
      localStorage.removeItem('cs-agent-username');
      window.location.reload();
      return Promise.reject(new ApiError('认证已过期，请重新登录', 401));
    }
    if (status === 403) return Promise.reject(new ApiError(extractMsg(data) || '无权限执行此操作', 403));
    if (status === 400) return Promise.reject(new ApiError(extractMsg(data) || '请求参数有误', 400));
    if (status === 500) return Promise.reject(new ApiError('服务器错误，请稍后重试', 500));
    return Promise.reject(new ApiError('请求失败', status));
  },
);

function extractMsg(data: unknown): string | null {
  if (typeof data === 'object' && data !== null) {
    const d = data as Record<string, unknown>;
    if (typeof d.error === 'string') return d.error;
    if (typeof d.message === 'string') return d.message;
  }
  return null;
}

export default agentClient;
