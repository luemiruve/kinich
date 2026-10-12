import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'

import { EditableItemSection } from '@/admin/EditableItemSection'
import { EditableTextSection } from '@/admin/EditableTextSection'
import { useAdminPage } from '@/admin/useAdminPage'
import { Button } from '@/components/Button'
import { PageStatus } from '@/components/PageStatus'
import { displayName, EMPTY_SITE } from '@/content/site'
import { draftToPublicPage } from '@/home/draftToPublicPage'
import { HomeSections } from '@/home/HomeSections'
import { ADMIN_HOME } from '@/routes/safeRedirect'
import { usePublicSite } from '@/site/usePublicSite'

/** Editar (borrador) → Previsualizar → Publicar por sección. Conserva la apariencia del sitio público. */
export function AdminInicioPage() {
  const { state, reload } = useAdminPage()
  const site = usePublicSite()
  const [mode, setMode] = useState<'edit' | 'preview'>('edit')
  const [notice, setNotice] = useState('')
  const [formsOpen, setFormsOpen] = useState<string[]>([])
  const headerRef = useRef<HTMLElement>(null)
  const ready = state.status === 'ready'

  /** Un aviso idéntico al anterior cambia de contenido (espacio fino final) para que la región aria-live lo re-anuncie. */
  const notify = useCallback((message: string) => {
    setNotice((previous) => (previous === message ? `${message}\u00A0` : message))
  }, [])

  const onFormOpenChange = useCallback((sectionKey: string, open: boolean) => {
    setFormsOpen((current) => {
      const has = current.includes(sectionKey)
      if (open === has) return current
      return open ? [...current, sectionKey] : current.filter((key) => key !== sectionKey)
    })
  }, [])

  useEffect(() => {
    document.title = 'Editar Inicio · Kinich'
  }, [])

  /** Publica la altura real de la cabecera sticky (puede partirse en varias líneas) para `scroll-padding-top`. */
  useEffect(() => {
    const header = headerRef.current
    if (!header) return undefined
    const root = document.documentElement
    const publish = () => root.style.setProperty('--kn-sticky-header', `${header.getBoundingClientRect().height}px`)
    publish()
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(publish)
    observer?.observe(header)
    return () => {
      observer?.disconnect()
      root.style.removeProperty('--kn-sticky-header')
    }
  }, [ready])

  if (state.status === 'loading') return <PageStatus message="Cargando Inicio…" />
  if (state.status === 'error') {
    return (
      <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-4 px-4 text-center">
        <p role="alert">{state.message}</p>
        <Button className="w-auto" onClick={() => void reload()}>Reintentar</Button>
      </main>
    )
  }

  const page = state.page
  const preview = draftToPublicPage(page)
  const orderedSections = [
    ...page.sections.filter((s) => s.sectionKey !== 'closing-cta'),
    ...page.sections.filter((s) => s.sectionKey === 'closing-cta'),
  ]

  return (
    <div className="min-h-dvh">
      <header ref={headerRef} className="sticky top-0 z-10 flex flex-wrap items-center justify-between gap-3 border-b border-outline-variant bg-surface px-4 py-2 sm:px-6 md:px-16">
        <Link to={ADMIN_HOME} className="label-caps min-h-11 content-center">← Panel</Link>
        {/* En previsualización el h1 es el de la portada (sr-only): aquí solo un párrafo. */}
        {mode === 'edit'
          ? <h1 className="font-serif text-xl">Editar Inicio</h1>
          : <p className="font-serif text-xl">Editar Inicio</p>}
        <div className="flex gap-1" role="group" aria-label="Modo">
          <Button variant="text" aria-pressed={mode === 'edit'} onClick={() => setMode('edit')}>Editar</Button>
          <Button variant="text" aria-pressed={mode === 'preview'} disabled={formsOpen.length > 0} onClick={() => setMode('preview')}>Previsualizar</Button>
        </div>
      </header>
      <p role="status" aria-label="Avisos" aria-live="polite" className="px-4 py-2 text-sm sm:px-6 md:px-16">{notice}</p>
      <main>
        {mode === 'preview' ? (
          <HomeSections page={preview} siteName={displayName(site ?? EMPTY_SITE)} />
        ) : (
          orderedSections.map((section) =>
            section.textEditable ? (
              <EditableTextSection key={section.sectionKey} section={section} reload={reload} notify={notify} onFormOpenChange={onFormOpenChange} />
            ) : (
              <EditableItemSection key={section.sectionKey} section={section} reload={reload} notify={notify} onFormOpenChange={onFormOpenChange} />
            ),
          )
        )}
      </main>
    </div>
  )
}
