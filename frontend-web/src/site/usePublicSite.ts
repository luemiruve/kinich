import { useEffect, useState } from 'react'

import { fetchPublicSite, type PublicSite } from '@/api/site'
import { EMPTY_SITE } from '@/content/site'

/**
 * Identidad pública de la instalación. Mientras carga devuelve null (sin parpadeo de un nombre provisional);
 * si falla, la instalación vacía: la identidad nunca bloquea el contenido.
 */
export function usePublicSite(load: (signal: AbortSignal) => Promise<PublicSite> = fetchPublicSite): PublicSite | null {
  const [site, setSite] = useState<PublicSite | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
      .then((loaded) => {
        if (!controller.signal.aborted) setSite(loaded)
      })
      .catch(() => {
        if (!controller.signal.aborted) setSite(EMPTY_SITE)
      })
    return () => controller.abort()
  }, [load])

  return site
}
