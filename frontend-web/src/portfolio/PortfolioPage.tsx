import { useEffect } from 'react'
import { useSearchParams } from 'react-router'

import type { PublicPortfolioPage } from '@/api/publicPortfolio'
import { Button } from '@/components/Button'
import { ClosingCta } from '@/components/editorial/ClosingCta'
import { PageHeader } from '@/components/editorial/PageHeader'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { displayName } from '@/content/site'
import { usePublicSite } from '@/site/usePublicSite'

import { CATEGORY_PARAM, categoryParam } from './categoryParam'
import { PortfolioFilters } from './PortfolioFilters'
import { ProjectGrid } from './ProjectGrid'
import { usePublicPortfolio } from './usePublicPortfolio'

const PAGE_TITLE = 'Portafolio'

const loading = (
  <div role="status" aria-label="Cargando contenido" className="grid min-h-[60svh] gap-4 p-4 sm:p-6 md:p-16">
    <span className="sr-only">Cargando…</span>
    <div className="bg-surface-container" />
  </div>
)

function PortfolioContent({ page }: { page: PublicPortfolioPage }) {
  const [search, setSearch] = useSearchParams()
  // Filtro en cliente: la categoría vigente sale de la URL; una desconocida equivale a «Todos».
  const selected = categoryParam.read(search, page.categories)
  const visible = selected === null ? page.projects : page.projects.filter((project) => project.category === selected)

  function select(category: string | null) {
    const next = new URLSearchParams(search)
    if (category === null) next.delete(CATEGORY_PARAM)
    else next.set(CATEGORY_PARAM, category)
    // Reemplaza: los filtros no llenan el historial (Atrás sale del portafolio).
    setSearch(next, { replace: true })
  }

  return (
    <>
      <PageHeader heading={page.pageHeader} fallbackTitle={PAGE_TITLE} />
      {page.projects.length === 0 ? (
        <p className="px-4 py-16 text-center text-on-surface-variant">Todavía no hay proyectos publicados.</p>
      ) : (
        <div className="flex flex-col gap-10 pb-16 md:pb-24">
          <PortfolioFilters categories={page.categories} selected={selected} onSelect={select} />
          <h2 className="sr-only">Proyectos</h2>
          <ProjectGrid projects={visible} />
        </div>
      )}
      <ClosingCta heading={page.closingCta} />
    </>
  )
}

export function PortfolioPage() {
  const { state, retry } = usePublicPortfolio()
  const site = usePublicSite()

  useEffect(() => {
    if (site) document.title = `${PAGE_TITLE} · ${displayName(site)}`
  }, [site])

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
      {state.status === 'ready' && <PortfolioContent page={state.page} />}
    </PublicLayout>
  )
}
