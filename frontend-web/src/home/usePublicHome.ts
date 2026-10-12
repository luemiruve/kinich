import { useCallback, useEffect, useState } from 'react'

import { ApiError } from '@/api/http'
import { fetchPublicHome, type PublicPage } from '@/api/publicPage'

export type PublicHomeState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; page: PublicPage }

export function usePublicHome(load: (signal: AbortSignal) => Promise<PublicPage> = fetchPublicHome) {
  const [state, setState] = useState<PublicHomeState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
      .then((page) => {
        if (!controller.signal.aborted) setState({ status: 'ready', page })
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setState({
          status: 'error',
          message: error instanceof ApiError ? error.message : 'No se pudo cargar la página.',
        })
      })
    return () => controller.abort()
  }, [load, attempt])

  const retry = useCallback(() => {
    setState({ status: 'loading' })
    setAttempt((n) => n + 1)
  }, [])
  return { state, retry }
}
