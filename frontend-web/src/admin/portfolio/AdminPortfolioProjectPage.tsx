import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useParams } from 'react-router'

import { Button } from '@/components/Button'
import { PageStatus } from '@/components/PageStatus'

import { ConfirmDeleteDialog } from '../ConfirmDeleteDialog'

import { CoverField } from './CoverField'
import { GalleryEditor } from './GalleryEditor'
import { ProjectForm } from './ProjectForm'
import { ProjectPreview } from './ProjectPreview'
import { canPublish, PENDING_WITHDRAWAL_HINT, statusLabel } from './projectStatus'
import { useProjectEditor } from './useProjectEditor'

const BACK = '/admin/portafolio'
const IMAGE_ERROR = /^images\[\d+\]/

/**
 * Editor de un proyecto (nuevo o existente): Guardar borrador → Previsualizar → Publicar. La clave por id remonta el
 * editor al pasar de «nuevo» al proyecto creado: estado y sugerencia de slug empiezan de cero.
 */
export function AdminPortfolioProjectPage() {
  const { id } = useParams()
  return <ProjectEditor key={id ?? 'new'} id={id} />
}

function ProjectEditor({ id }: { id: string | undefined }) {
  const location = useLocation()
  const initialNotice = (location.state as { notice?: string } | null)?.notice ?? ''
  const editor = useProjectEditor(id, initialNotice)
  const { load, project, draft, dirty, blocker, fieldErrors, setFieldErrors, busy, notice } = editor
  const [mode, setMode] = useState<'edit' | 'preview'>('edit')
  const title = id ? 'Editar proyecto' : 'Nuevo proyecto'

  useEffect(() => {
    document.title = `${title} · Kinich`
  }, [title])

  if (load.status === 'loading') return <PageStatus message="Cargando proyecto…" />
  if (load.status === 'notFound' || load.status === 'error') {
    return (
      <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-4 px-4 text-center">
        <p role="alert">{load.status === 'notFound' ? 'Este proyecto ya no existe.' : load.message}</p>
        <Link to={BACK} className="label-caps underline decoration-secondary underline-offset-4">Volver a Portafolio</Link>
      </main>
    )
  }

  const publishable = !!project && !dirty && canPublish(project)
  const clearImageErrors = () =>
    setFieldErrors((current) => Object.fromEntries(Object.entries(current).filter(([key]) => !IMAGE_ERROR.test(key))))

  function submit(event: FormEvent) {
    event.preventDefault()
    void editor.save()
  }

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 flex flex-wrap items-center justify-between gap-3 border-b border-outline-variant bg-surface px-4 py-2 sm:px-6 md:px-16">
        <Link to={BACK} className="label-caps min-h-11 content-center">← Portafolio</Link>
        {/* En previsualización el h1 es el del proyecto: aquí solo un párrafo. */}
        {mode === 'edit' ? <h1 className="font-serif text-xl">{title}</h1> : <p className="font-serif text-xl">{title}</p>}
        <div className="flex gap-1" role="group" aria-label="Modo">
          <Button variant="text" aria-pressed={mode === 'edit'} onClick={() => setMode('edit')}>Editar</Button>
          <Button variant="text" aria-pressed={mode === 'preview'} onClick={() => setMode('preview')}>Previsualizar</Button>
        </div>
      </header>
      <p role="status" aria-label="Avisos" aria-live="polite" className="px-4 py-2 text-sm sm:px-6 md:px-16">{notice}</p>
      {mode === 'preview' ? (
        <main className="pb-16"><ProjectPreview draft={draft} categoryLabel={project?.category?.label ?? null} /></main>
      ) : (
        <main className="mx-auto w-full max-w-3xl px-4 pb-16 sm:px-6">
          <form onSubmit={submit} className="flex flex-col gap-10">
            <ProjectForm draft={draft} publishedSlug={project?.publishedSlug ?? null} fieldErrors={fieldErrors}
              onTitle={editor.setTitle} onSlug={editor.setSlug} onChange={editor.update} />
            <CoverField cover={draft.cover} fieldErrors={fieldErrors} onChange={(cover) => editor.update({ cover })} />
            <GalleryEditor images={draft.images} fieldErrors={fieldErrors} onChange={(images) => editor.update({ images })}
              onStructureChange={clearImageErrors} />
            <div className="flex flex-col gap-3 border-t border-outline-variant pt-4">
              {project && (
                <p className="text-sm text-on-surface-variant">
                  Estado: <strong className="label-caps">{statusLabel(project)}</strong>
                  {project.status === 'PUBLISHED' && project.hidden && ` · ${PENDING_WITHDRAWAL_HINT}`}
                </p>
              )}
              {project && dirty && <p className="text-sm">Hay cambios sin guardar: guarda el borrador antes de publicar.</p>}
              <div className="flex flex-wrap justify-end gap-2">
                <Button type="submit" className="w-auto" disabled={busy}>Guardar borrador</Button>
                {project && (
                  <Button className="w-auto" disabled={busy || !publishable} onClick={() => void editor.publish()}>Publicar</Button>
                )}
              </div>
            </div>
          </form>
        </main>
      )}
      {blocker.state === 'blocked' && (
        <ConfirmDeleteDialog title="¿Salir sin guardar?" description="Los cambios sin guardar se perderán."
          cancelLabel="Seguir editando" confirmLabel="Salir sin guardar"
          onCancel={() => blocker.reset()} onConfirm={() => blocker.proceed()} />
      )}
    </div>
  )
}
