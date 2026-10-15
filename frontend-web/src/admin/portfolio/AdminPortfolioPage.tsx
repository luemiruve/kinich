import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link } from 'react-router'

import * as pageApi from '@/api/adminPage'
import type { AdminPortfolioSection, PortfolioSectionKey } from '@/api/adminPage'
import * as portfolioApi from '@/api/adminPortfolio'
import type { AdminProjectSummary } from '@/api/adminPortfolio'
import { Button } from '@/components/Button'
import { PageStatus } from '@/components/PageStatus'
import { PORTFOLIO_LIMITS } from '@/content/textLimits'
import { ADMIN_HOME } from '@/routes/safeRedirect'

import { ConfirmDeleteDialog } from '../ConfirmDeleteDialog'
import { SectionHeadingEditor } from '../SectionHeadingEditor'
import { SectionPublishBar } from '../SectionPublishBar'
import { MAX_PROJECTS_MESSAGE } from './messages'
import { PortfolioOrderBar } from './PortfolioOrderBar'
import { ProjectListItem, type MoveDirection } from './ProjectListItem'
import { usePortfolioAdmin } from './usePortfolioAdmin'

const SECTION_LABELS: Record<PortfolioSectionKey, string> = {
  'page-header': 'Cabecera de la página',
  'closing-cta': 'Cierre',
}

const refs = (projects: AdminProjectSummary[]) => projects.map(({ id, version }) => ({ id, version }))

/** Lista de proyectos (orden, publicación, retirada, eliminación) y textos editoriales de la página. */
export function AdminPortfolioPage() {
  const { state, reload, run, busy, notice, notify } = usePortfolioAdmin()
  const [deleting, setDeleting] = useState<AdminProjectSummary | null>(null)
  const [formsOpen, setFormsOpen] = useState<string[]>([])
  // Tras mover, el foco vuelve al mismo botón del proyecto movido (o al otro si ya está en un extremo).
  const pendingFocus = useRef<{ id: string; direction: MoveDirection } | null>(null)
  const list = useRef<HTMLUListElement>(null)

  useEffect(() => {
    document.title = 'Editar Portafolio · Kinich'
  }, [])

  useLayoutEffect(() => {
    const target = pendingFocus.current
    if (!target || busy || !list.current) return
    pendingFocus.current = null
    const other = target.direction === 'up' ? 'down' : 'up'
    const button = list.current.querySelector<HTMLElement>(`[data-move="${target.id}:${target.direction}"]`)
      ?? list.current.querySelector<HTMLElement>(`[data-move="${target.id}:${other}"]`)
    button?.focus()
  }, [state, busy])

  const onFormOpenChange = useCallback((key: string, open: boolean) => {
    setFormsOpen((current) => {
      const has = current.includes(key)
      if (open === has) return current
      return open ? [...current, key] : current.filter((k) => k !== key)
    })
  }, [])

  if (state.status === 'loading') return <PageStatus message="Cargando Portafolio…" />
  if (state.status === 'error') {
    return (
      <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-4 px-4 text-center">
        <p role="alert">{state.message}</p>
        <Button className="w-auto" onClick={() => void reload()}>Reintentar</Button>
      </main>
    )
  }

  const projects = [...state.list.projects].sort((a, b) => a.position - b.position)
  const full = projects.length >= PORTFOLIO_LIMITS.projects
  const locked = busy || formsOpen.length > 0

  function move(index: number, direction: MoveDirection) {
    const next = [...projects]
    const target = index + (direction === 'up' ? -1 : 1)
    ;[next[index], next[target]] = [next[target], next[index]]
    pendingFocus.current = { id: projects[index].id, direction }
    void run(() => portfolioApi.reorderProjects(refs(next)), 'Orden guardado en el borrador')
  }

  function toggleHidden(project: AdminProjectSummary) {
    // Publicado y oculto = retirada pendiente: mostrarlo la cancela sin tener que publicar.
    const message = project.hidden
      ? project.status === 'PUBLISHED'
        ? 'Se cancela la retirada; sigue visible en el sitio.'
        : 'Se mostrará en el sitio al publicar el proyecto'
      : project.status === 'PUBLISHED'
        ? 'Se retirará del sitio al publicar el proyecto'
        : 'Proyecto oculto: no se publicará mientras siga oculto'
    void run(() => portfolioApi.setProjectVisibility(project.id, !project.hidden, project.version), message)
  }

  return (
    <div className="min-h-dvh">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-outline-variant bg-surface px-4 py-2 sm:px-6 md:px-16">
        <Link to={ADMIN_HOME} className="label-caps min-h-11 content-center">← Panel</Link>
        <h1 className="font-serif text-xl">Editar Portafolio</h1>
      </header>
      <p role="status" aria-label="Avisos" aria-live="polite" className="px-4 py-2 text-sm sm:px-6 md:px-16">{notice}</p>
      <main className="flex flex-col gap-12 px-4 pb-16 sm:px-6 md:px-16">
        <section aria-labelledby="portfolio-projects" className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 id="portfolio-projects" className="font-serif text-2xl">Proyectos</h2>
            {full ? (
              <Button className="w-auto" disabled>Nuevo proyecto</Button>
            ) : (
              <Link to="/admin/portafolio/nuevo"
                className="label-caps inline-flex h-12 items-center bg-primary-container px-4 text-on-primary hover:bg-primary">
                Nuevo proyecto
              </Link>
            )}
          </div>
          {full && <p className="text-sm">{MAX_PROJECTS_MESSAGE}</p>}
          <p className="text-sm text-on-surface-variant">
            Subir y Bajar guardan el orden en el borrador; el sitio lo usa cuando publicas el orden. «Retirar» se aplica
            al publicar el proyecto; «Eliminar» es inmediato.
          </p>
          <PortfolioOrderBar orderModified={state.list.orderModified} disabled={locked}
            onPublish={() => void run(() => portfolioApi.publishOrder(refs(projects)), 'Orden publicado')} />
          {projects.length === 0 ? (
            <p className="text-on-surface-variant">Todavía no hay proyectos.</p>
          ) : (
            <ul ref={list} className="flex flex-col gap-6">
              {projects.map((project, index) => (
                <ProjectListItem
                  key={project.id}
                  project={project}
                  canMoveUp={index > 0}
                  canMoveDown={index < projects.length - 1}
                  disabled={locked}
                  onMove={(direction) => move(index, direction)}
                  onPublish={() => void run(
                    () => portfolioApi.publishProject(project.id, project.version),
                    project.hidden ? 'Proyecto retirado del sitio' : 'Proyecto publicado',
                  )}
                  onToggleHidden={() => toggleHidden(project)}
                  onDelete={() => setDeleting(project)}
                />
              ))}
            </ul>
          )}
        </section>
        <section aria-labelledby="portfolio-texts" className="flex flex-col gap-8">
          <h2 id="portfolio-texts" className="font-serif text-2xl">Textos de la página</h2>
          {state.page.sections.map((section) => (
            <PageTextSection key={section.sectionKey} section={section} disabled={locked} reload={reload} notify={notify}
              onFormOpenChange={onFormOpenChange}
              onPublish={() => void run(() => pageApi.publishSection(section.sectionKey, section.revision, 'portfolio'), 'Sección publicada')} />
          ))}
        </section>
      </main>
      {deleting && (
        <ConfirmDeleteDialog
          label={deleting.title}
          description="Se elimina de inmediato el borrador y, si estaba publicado, desaparece del sitio. No se puede deshacer."
          onCancel={() => setDeleting(null)}
          onConfirm={() => {
            const target = deleting
            setDeleting(null)
            void run(() => portfolioApi.deleteProject(target.id, target.version), 'Proyecto eliminado')
          }}
        />
      )}
    </div>
  )
}

interface PageTextSectionProps {
  section: AdminPortfolioSection
  disabled: boolean
  reload: () => Promise<void>
  notify: (message: string) => void
  onFormOpenChange: (key: string, open: boolean) => void
  onPublish: () => void
}

/** Cabecera editorial de PAGE_HEADER o CLOSING_CTA: borrador → publicar la sección (como en Inicio). */
function PageTextSection({ section, disabled, reload, notify, onFormOpenChange, onPublish }: PageTextSectionProps) {
  const label = SECTION_LABELS[section.sectionKey]
  return (
    <section aria-label={label} className="flex flex-col gap-4">
      <h3 className="font-serif text-xl">{label}</h3>
      <SectionPublishBar section={section} disabled={disabled} onPublish={onPublish} />
      <SectionHeadingEditor section={section} page="portfolio" reload={reload} notify={notify}
        onFormOpenChange={onFormOpenChange} disabled={disabled} />
    </section>
  )
}
