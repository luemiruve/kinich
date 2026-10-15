import { useEffect } from 'react'
import { Link, useParams } from 'react-router'

import type { PublicProject } from '@/api/publicPortfolio'
import { Button } from '@/components/Button'
import { Eyebrow } from '@/components/editorial/Eyebrow'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { displayName } from '@/content/site'
import { usePublicSite } from '@/site/usePublicSite'

import { ProjectGallery } from './ProjectGallery'
import { ProjectNotFound } from './ProjectNotFound'
import { usePublicProject } from './usePublicProject'

const NOT_FOUND_TITLE = 'Proyecto no encontrado'

const loading = (
  <div role="status" aria-label="Cargando contenido" className="grid min-h-[60svh] gap-4 p-4 sm:p-6 md:p-16">
    <span className="sr-only">Cargando…</span>
    <div className="bg-surface-container" />
  </div>
)

/** Contenido del detalle; también lo usa la vista previa del panel con los datos del borrador. */
export function PortfolioProjectContent({ project }: { project: PublicProject }) {
  return (
    <article className="mx-auto flex w-full max-w-6xl flex-col gap-10 px-4 py-10 sm:px-6 md:py-16">
      <Link to="/portafolio" className="label-caps inline-flex min-h-11 items-center self-start text-primary underline underline-offset-4">
        Volver al portafolio
      </Link>
      <header className="flex max-w-3xl flex-col gap-3">
        {project.category && <p className="label-caps text-on-surface-variant">{project.category}</p>}
        {project.eyebrow && <Eyebrow>{project.eyebrow}</Eyebrow>}
        <h1 className="font-serif text-4xl break-words [overflow-wrap:anywhere] md:text-6xl">{project.title}</h1>
        {project.description && (
          <p className="whitespace-pre-line break-words [overflow-wrap:anywhere] text-on-surface-variant">{project.description}</p>
        )}
      </header>
      <ProjectGallery images={project.images} />
    </article>
  )
}

export function PortfolioProjectPage() {
  const { slug = '' } = useParams()
  const { state, retry } = usePublicProject(slug)
  const site = usePublicSite()

  const pageTitle =
    state.status === 'ready' ? state.project.title : state.status === 'notFound' ? NOT_FOUND_TITLE : null

  useEffect(() => {
    if (site && pageTitle) document.title = `${pageTitle} · ${displayName(site)}`
  }, [site, pageTitle])

  if (!site) return <main>{loading}</main>

  return (
    <PublicLayout site={site}>
      {state.status === 'loading' && loading}
      {state.status === 'notFound' && <ProjectNotFound />}
      {state.status === 'error' && (
        <div className="mx-auto flex min-h-[60svh] max-w-md flex-col items-center justify-center gap-4 px-4 text-center">
          <p role="alert" className="text-on-surface-variant">{state.message}</p>
          <Button className="w-auto" onClick={retry}>Reintentar</Button>
        </div>
      )}
      {state.status === 'ready' && <PortfolioProjectContent project={state.project} />}
    </PublicLayout>
  )
}
