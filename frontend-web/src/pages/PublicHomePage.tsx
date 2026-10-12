import { useEffect } from 'react'

import { Button } from '@/components/Button'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { displayName } from '@/content/site'
import { HomeSections } from '@/home/HomeSections'
import { usePublicHome } from '@/home/usePublicHome'
import { usePublicSite } from '@/site/usePublicSite'

const loading = (
  <div role="status" aria-label="Cargando contenido" className="grid min-h-[60svh] gap-4 p-4 sm:p-6 md:p-16">
    <span className="sr-only">Cargando…</span>
    <div className="bg-surface-container" />
  </div>
)

export function PublicHomePage() {
  const { state, retry } = usePublicHome()
  const site = usePublicSite()

  useEffect(() => {
    if (site) document.title = displayName(site)
  }, [site])

  // Hasta conocer la identidad no se pinta la cabecera: evita mostrar un nombre provisional y cambiarlo después.
  if (!site) return <main>{loading}</main>

  return (
    <PublicLayout site={site}>
      {state.status === 'loading' && loading}
      {state.status === 'error' && (
        <div className="mx-auto flex min-h-[60svh] max-w-md flex-col items-center justify-center gap-4 px-4 text-center">
          <p role="alert" className="text-on-surface-variant">{state.message}</p>
          <Button className="w-auto" onClick={retry}>Reintentar</Button>
        </div>
      )}
      {state.status === 'ready' && <HomeSections page={state.page} siteName={displayName(site)} />}
    </PublicLayout>
  )
}
