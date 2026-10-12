import { useCallback, useEffect, useState } from 'react'

import { fetchAdminSettings, type AdminSettings } from '@/api/adminSettings'
import { ApiError } from '@/api/http'
import { useSession } from '@/session/useSession'

export type SettingsState =
  | { status: 'loading' }
  | { status: 'error'; message: string; unauthorized: boolean }
  | { status: 'ready'; settings: AdminSettings }

async function loadState(signal?: AbortSignal): Promise<SettingsState> {
  try {
    return { status: 'ready', settings: await fetchAdminSettings(signal) }
  } catch (error) {
    return {
      status: 'error',
      message: error instanceof ApiError ? error.message : 'No se pudo cargar la configuración.',
      unauthorized: error instanceof ApiError && error.status === 401,
    }
  }
}

/**
 * Configuración de la instalación y escrituras con la misma gestión de errores que la edición de Inicio:
 * 401 cierra la sesión, 400 devuelve los errores por campo y 409 recarga (la versión cambió en otra pestaña).
 */
export function useSettings(notify: (message: string) => void) {
  const [state, setState] = useState<SettingsState>({ status: 'loading' })
  // Cambia tras un 409: la página remonta los formularios con los datos actuales del servidor.
  const [conflicts, setConflicts] = useState(0)
  const { expire } = useSession()

  // Sesión caducada: RequireAuth manda al login recordando /admin/configuracion.
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

  /** Ejecuta una escritura; si devuelve la configuración completa la aplica, si no recarga. true si se guardó. */
  const run = useCallback(async (
    action: () => Promise<AdminSettings | unknown>,
    success: string,
    onFieldErrors: (errors: Record<string, string>) => void,
  ): Promise<boolean> => {
    onFieldErrors({})
    try {
      const result = await action()
      if (result && typeof result === 'object' && 'identityConfigured' in result) {
        setState({ status: 'ready', settings: result as AdminSettings })
      } else {
        await reload()
      }
      notify(success)
      return true
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        expire()
        return false
      }
      if (error instanceof ApiError && error.status === 400 && Object.keys(error.fieldErrors).length > 0) {
        onFieldErrors(error.fieldErrors)
        return false
      }
      notify(error instanceof ApiError ? error.message : 'No se pudo guardar.')
      if (error instanceof ApiError && error.code === 'VERSION_CONFLICT') {
        await reload()
        setConflicts((n) => n + 1)
      }
      return false
    }
  }, [expire, notify, reload])

  return { state, reload, run, conflicts }
}
