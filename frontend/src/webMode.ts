/// <reference types="vite/client" />
import type { Role } from './types'

export const adminOnly = import.meta.env.MODE === 'admin'

export function homeForRole(role?: Role) {
  if (role === 'DATA_ADMIN') return '/data/spaces'
  if (role === 'SERVER_ADMIN') return '/system'
  return adminOnly ? '/login' : '/spaces'
}
