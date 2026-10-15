import { useCallback, useEffect, useState } from 'react'

import { fetchAdminPortfolioPage, type AdminPortfolioPage } from '@/api/adminPage'
import { fetchProjects, type AdminProjectList } from '@/api/adminPortfolio'
import { ApiError } from '@/api/http'
import { useSession } from '@/session/useSession'

import { MAX_PROJECTS_MESSAGE, RELOADED } from './messages'

export type PortfolioAdminState =
  | { status: 'loading' }
  | { status: 'error'; message: string; unauthorized: boolean }
  | { status: 'ready'; list: AdminProjectList; page: AdminPortfolioPage }

async function loadState(signal?: AbortSignal): Promise<PortfolioAdminState> {
  try {
    const [list, page] = await Promise.all([fetchProjects(signal), fetchAdminPortfolioPage(signal)])
    return { status: 'ready', list, page }
  } catch (error) {
    return {
      status: 'error',
      message: error instanceof ApiError ? error.message : 'No se pudo cargar Portafolio.',
      unauthorized: error instanceof ApiError && error.status === 401,
    }
  }
}

/**
 * Lista de proyectos y textos de la página. El GET es la única fuente de verdad: reordenar, publicar, retirar y
 * eliminar cambian versiones, así que tras cada escritura (correcta o no) se recarga y nunca quedan versiones viejas.
 */
export function usePortfolioAdmin() {
  const [state, setState] = useState<PortfolioAdminState>({ status: 'loading' })
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const { expire } = useSession()

  /** Un aviso idéntico al anterior cambia de contenido (espacio fino final) para que la región aria-live lo re-anuncie. */
  const notify = useCallback((message: string) => {
    setNotice((previous) => (previous === message ? `${message} ` : message))
  }, [])

  // Sesión caducada: RequireAuth manda al login recordando /admin/portafolio.
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

  /** Ejecuta una escritura: 401 cierra sesión; 409/404 avisan y recargan con los datos del servidor. */
  async function run(action: () => Promise<unknown>, success: string): Promise<boolean> {
    setBusy(true)
    try {
      await action()
      notify(success)
      await reload()
      return true
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        expire()
        return false
      }
      if (error instanceof ApiError && error.code === 'MAX_ITEMS_REACHED') {
        notify(MAX_PROJECTS_MESSAGE)
      } else if (error instanceof ApiError && (error.status === 409 || error.status === 404)) {
        notify(`${error.message} ${RELOADED}`)
      } else {
        notify(error instanceof ApiError ? error.message : 'No se pudo guardar.')
      }
      await reload()
      return false
    } finally {
      setBusy(false)
    }
  }

  return { state, reload, run, busy, notice, notify }
}
