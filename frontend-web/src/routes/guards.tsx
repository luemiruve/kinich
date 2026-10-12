import type { ReactNode } from 'react'
import { Navigate, useLocation, useSearchParams } from 'react-router'

import { PageStatus } from '@/components/PageStatus'
import { useSession } from '@/session/useSession'

import { safeRedirect } from './safeRedirect'

export const LOGIN_PATH = '/admin/login'

/** Rutas del panel: sin sesión se manda al login recordando el destino. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useSession()
  const location = useLocation()

  if (status === 'loading') {
    return <PageStatus message="Verificando sesión…" />
  }
  if (status === 'anonymous') {
    const redirect = encodeURIComponent(location.pathname)
    return <Navigate to={`${LOGIN_PATH}?redirect=${redirect}`} replace />
  }
  return children
}

/** El login no se muestra a quien ya tiene sesión; tras iniciarla se navega al destino recordado. */
export function GuestOnly({ children }: { children: ReactNode }) {
  const { status } = useSession()
  const [searchParams] = useSearchParams()

  if (status === 'loading') {
    return <PageStatus message="Verificando sesión…" />
  }
  if (status === 'authenticated') {
    return <Navigate to={safeRedirect(searchParams.get('redirect'))} replace />
  }
  return children
}
