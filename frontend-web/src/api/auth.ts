import { request } from './http'

export interface SessionUser {
  email: string
  fullName: string
}

export function login(email: string, password: string): Promise<SessionUser> {
  return request<SessionUser>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function logout(): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST' })
}

export function fetchCurrentUser(): Promise<SessionUser> {
  return request<SessionUser>('/api/auth/me')
}
