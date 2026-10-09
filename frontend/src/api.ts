import { ref } from 'vue'
import type { Account } from './types'

export const account = ref<Account | null>(null)
let csrf: { headerName: string; token: string } | null = null
export class ApiFailure extends Error {
  constructor(public status: number, public code: string, message: string) { super(message) }
}
export async function refreshCsrf() {
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  if (!response.ok) throw new Error('无法获取安全令牌，请检查服务器连接')
  csrf = await response.json()
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers = new Headers(options.headers)
  if (method !== 'GET') {
    if (!csrf) await refreshCsrf()
    headers.set(csrf!.headerName, csrf!.token)
  }
  const response = await fetch('/api' + path, { ...options, headers, credentials: 'same-origin' })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ code: 'REQUEST_FAILED', message: '请求失败，请稍后重试' }))
    if (response.status === 401 && !path.startsWith('/auth/login')) {
      account.value = null
      window.dispatchEvent(new Event('campusflow-session-expired'))
    }
    throw new ApiFailure(response.status, error.code, error.message)
  }
  return response.status === 204 ? undefined as T : await response.json()
}
export async function login(username: string, password: string) {
  await refreshCsrf()
  await api('/auth/login', { method: 'POST', body: new URLSearchParams({ username, password }) })
  // 登录会轮换安全令牌，后续写操作必须使用新令牌。
  await refreshCsrf()
  account.value = await api<Account>('/auth/me')
}
export async function logout() {
  await api('/auth/logout', { method: 'POST' })
  account.value = null; csrf = null
}
export function jsonRequest(method: string, body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}
