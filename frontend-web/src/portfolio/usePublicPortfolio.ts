import { useCallback, useEffect, useState } from 'react'

import { ApiError } from '@/api/http'
import { fetchPublicPortfolio, type PublicPortfolioPage } from '@/api/publicPortfolio'

export type PublicPortfolioState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; page: PublicPortfolioPage }

export function usePublicPortfolio(
  load: (signal: AbortSignal) => Promise<PublicPortfolioPage> = fetchPublicPortfolio,
) {
  const [state, setState] = useState<PublicPortfolioState>({ status: 'loading' })
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
