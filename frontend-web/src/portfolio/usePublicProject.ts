import { useCallback, useEffect, useState } from 'react'

import { ApiError } from '@/api/http'
import { fetchPublicProject, type PublicProject } from '@/api/publicPortfolio'

export type PublicProjectState =
  | { status: 'loading' }
  | { status: 'notFound' }
  | { status: 'error'; message: string }
  | { status: 'ready'; project: PublicProject }

export function usePublicProject(
  slug: string,
  load: (slug: string, signal: AbortSignal) => Promise<PublicProject> = fetchPublicProject,
) {
  // El resultado se guarda con su slug: si la URL cambia, el resultado anterior se ignora (cargando) sin setState en el efecto.
  const [result, setResult] = useState<{ slug: string; state: PublicProjectState } | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    load(slug, controller.signal)
      .then((project) => {
        if (!controller.signal.aborted) setResult({ slug, state: { status: 'ready', project } })
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        // 404 = desconocido o no publicado: vista propia, nunca un error reintentable.
        if (error instanceof ApiError && error.status === 404) {
          setResult({ slug, state: { status: 'notFound' } })
          return
        }
        setResult({
          slug,
          state: {
            status: 'error',
            message: error instanceof ApiError ? error.message : 'No se pudo cargar el proyecto.',
          },
        })
      })
    return () => controller.abort()
  }, [slug, load, attempt])

  const retry = useCallback(() => {
    setResult(null)
    setAttempt((n) => n + 1)
  }, [])
  const state: PublicProjectState = result && result.slug === slug ? result.state : { status: 'loading' }
  return { state, retry }
}
