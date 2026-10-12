import { useCallback, useEffect, useState } from 'react'

import { fetchAdminHome, type AdminPage } from '@/api/adminPage'
import { ApiError } from '@/api/http'
import { useSession } from '@/session/useSession'

export type AdminState =
  | { status: 'loading' }
  | { status: 'error'; message: string; unauthorized: boolean }
  | { status: 'ready'; page: AdminPage }

async function loadState(signal?: AbortSignal): Promise<AdminState> {
  try {
    return { status: 'ready', page: await fetchAdminHome(signal) }
  } catch (error) {
    return {
      status: 'error',
      message: error instanceof ApiError ? error.message : 'No se pudo cargar Inicio.',
      unauthorized: error instanceof ApiError && error.status === 401,
    }
  }
}

/** El GET de administración es la única fuente de verdad: tras cada escritura se recarga. */
export function useAdminPage() {
  const [state, setState] = useState<AdminState>({ status: 'loading' })
  const { expire } = useSession()

  // Sesión caducada: RequireAuth manda al login recordando /admin/inicio.
  useEffect(() => {
    if (state.status === 'error' && state.unauthorized) expire()
  }, [state, expire])

  const reload = useCallback(async () => {
    setState(await loadState())
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    void loadState(controller.signal).then((next) => {
      if (!controller.signal.aborted) setState(next)
    })
    return () => controller.abort()
  }, [])

  return { state, reload }
}
