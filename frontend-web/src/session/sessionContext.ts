import { createContext } from 'react'

import type { SessionUser } from '@/api/auth'

export type SessionStatus = 'loading' | 'authenticated' | 'anonymous'

/**
 * Estado de sesión del panel. Es solo una ayuda de navegación: la autorización real
 * la decide el backend en cada petición.
 */
export interface SessionValue {
  status: SessionStatus
  user: SessionUser | null
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  /** La API respondió 401: la sesión caducó. Marca al usuario como anónimo para que el guardián lo lleve al login. */
  expire: () => void
}

export const SessionContext = createContext<SessionValue | null>(null)
