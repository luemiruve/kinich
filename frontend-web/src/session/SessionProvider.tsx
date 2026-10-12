import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import * as authApi from '@/api/auth'
import type { SessionUser } from '@/api/auth'

import { SessionContext, type SessionStatus, type SessionValue } from './sessionContext'

export function SessionProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<SessionStatus>('loading')
  const [user, setUser] = useState<SessionUser | null>(null)

  useEffect(() => {
    let active = true
    authApi
      .fetchCurrentUser()
      .then((current) => {
        if (!active) return
        setUser(current)
        setStatus('authenticated')
      })
      // Sin sesión válida (401) o sin conexión: se muestra el login, que informará del error al enviar.
      .catch(() => {
        if (active) setStatus('anonymous')
      })
    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const current = await authApi.login(email.trim(), password)
    setUser(current)
    setStatus('authenticated')
  }, [])

  // Si la API falla, la cookie HttpOnly sigue en el navegador: no se finge un cierre de sesión.
  const logout = useCallback(async () => {
    await authApi.logout()
    setUser(null)
    setStatus('anonymous')
  }, [])

  const expire = useCallback(() => {
    setUser(null)
    setStatus('anonymous')
  }, [])

  const value = useMemo<SessionValue>(
    () => ({ status, user, login, logout, expire }),
    [status, user, login, logout, expire],
  )

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}
