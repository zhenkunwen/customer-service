import axios, { AxiosError } from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1/cs';
const TIMEOUT = Number(import.meta.env.VITE_REQUEST_TIMEOUT) || 60000;

const client = axios.create({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
  headers: { 'Content-Type': 'application/json' },
});

const defaultApiKeys: Record<string, string> = {
  default: 'change-me',
  'tenant-a': 'change-me',
  'tenant-b': 'change-me',
};

// 请求拦截器：自动注入 API Key
client.interceptors.request.use((config) => {
  try {
    // 从 localStorage 读取当前租户的 API Key，无则使用默认值
    const tenantId = localStorage.getItem('cs-tenant-id') || 'default';
    const saved = localStorage.getItem(`cs-apikey-${tenantId}`);
    const key = saved || defaultApiKeys[tenantId] || '';
    config.headers['X-API-Key'] = key;
  } catch { /* ignore */ }
  return config;
});

// 响应拦截器：统一错误分类
client.interceptors.response.use(
  (res) => res,
  (error: AxiosError) => {
    if (axios.isCancel(error)) {
      return Promise.reject(new ApiError('请求已取消', 0));
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new ApiError('请求超时，请稍后重试', 408));
    }
    if (!error.response) {
      return Promise.reject(new ApiError('网络异常，请检查连接', 0));
    }
    const { status, data } = error.response;
    if (status === 400) {
      // Spring 校验错误：提取字段错误信息
      const msg = extractValidationMessage(data);
      return Promise.reject(new ApiError(msg || '请求参数有误', 400));
    }
    if (status === 429) return Promise.reject(new ApiError('请求过快，请稍等', 429));
    if (status === 500) return Promise.reject(new ApiError('服务器错误，请稍后重试', 500));
    return Promise.reject(new ApiError('请求失败', status));
  },
);

function extractValidationMessage(data: unknown): string | null {
  if (typeof data === 'object' && data !== null) {
    const d = data as Record<string, unknown>;
    // Spring Boot 默认 validation 错误格式
    if (Array.isArray(d.errors)) {
      const msgs = (d.errors as Array<{ defaultMessage?: string }>)
        .map((e) => e.defaultMessage)
        .filter(Boolean);
      if (msgs.length > 0) return msgs.join('；');
    }
    if (typeof d.message === 'string') return d.message;
    if (typeof d.error === 'string') return d.error;
  }
  return null;
}

export class ApiError extends Error {
  code: number;
  constructor(message: string, code: number) {
    super(message);
    this.code = code;
    this.name = 'ApiError';
  }
}

export default client;
