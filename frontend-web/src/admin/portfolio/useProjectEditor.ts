import { useCallback, useEffect, useRef, useState } from 'react'
import { useBlocker, useNavigate } from 'react-router'

import * as portfolioApi from '@/api/adminPortfolio'
import type { AdminProject, ProjectValues } from '@/api/adminPortfolio'
import { ApiError } from '@/api/http'
import type { Crop } from '@/api/publicPage'
import { useSession } from '@/session/useSession'

import type { FramingImage } from '../FramingEditor'
import { MAX_IMAGES_MESSAGE, MAX_PROJECTS_MESSAGE, RELOADED } from './messages'
import { slugify } from './slugify'

/** Imagen de la galería en edición; `key` es local (estable al reordenar). */
export interface GalleryEntry {
  key: string
  mediaId: string
  alt: string
  caption: string
  image: FramingImage
}

export interface ProjectDraft {
  title: string
  slug: string
  eyebrow: string
  categoryId: string | null
  description: string
  cover: { mediaId: string; alt: string; crop: Crop | null; image: FramingImage | null }
  images: GalleryEntry[]
}

export type EditorLoad = { status: 'loading' } | { status: 'notFound' } | { status: 'error'; message: string } | { status: 'ready' }

let nextKey = 0
export const newEntryKey = () => `img-${++nextKey}`

const EMPTY_DRAFT: ProjectDraft = {
  title: '', slug: '', eyebrow: '', categoryId: null, description: '',
  cover: { mediaId: '', alt: '', crop: null, image: null }, images: [],
}

function fromProject(project: AdminProject): ProjectDraft {
  return {
    title: project.title,
    slug: project.slug,
    eyebrow: project.eyebrow ?? '',
    categoryId: project.category?.id ?? null,
    description: project.description ?? '',
    cover: { mediaId: project.coverMediaId, alt: project.cover.alt, crop: project.cover.crop, image: project.cover },
    images: project.images.map((entry) => ({
      key: newEntryKey(), mediaId: entry.mediaId, alt: entry.image.alt, caption: entry.caption ?? '', image: entry.image,
    })),
  }
}

export function toValues(draft: ProjectDraft): ProjectValues {
  return {
    slug: draft.slug,
    title: draft.title,
    eyebrow: draft.eyebrow,
    categoryId: draft.categoryId,
    description: draft.description,
    cover: { mediaId: draft.cover.mediaId, alt: draft.cover.alt, crop: draft.cover.crop },
    images: draft.images.map(({ mediaId, alt, caption }) => ({ mediaId, alt, caption })),
  }
}

/**
 * El recorte de la portada puede llegar como `crop` (ItemFraming), `cover.crop` (normalizador) o `cover.crop.x`…
 * (Bean Validation): todos se muestran en el encuadre. `cover` (portada ausente) va con el selector de portada.
 */
export function mapFieldErrors(errors: Record<string, string>): Record<string, string> {
  const mapped: Record<string, string> = {}
  for (const [key, message] of Object.entries(errors)) {
    const target = key === 'crop' || key === 'cover.crop' || key.startsWith('cover.crop.') ? 'cover.crop'
      : key === 'cover' ? 'cover.mediaId' : key
    mapped[target] ??= message
  }
  return mapped
}

/** Claves que el formulario muestra junto a un campo; el resto se anuncia como aviso general. */
const SHOWN = /^(title|slug|eyebrow|categoryId|description|cover\.(mediaId|alt|crop)|images(\[\d+\](\.(mediaId|alt|caption))?)?)$/

/**
 * Estado del editor de un proyecto: borrador del formulario frente al último guardado, escrituras con gestión de
 * 401/400/409 (un 409 recarga y repone los datos del servidor) y slug que sigue al título hasta editarlo a mano.
 */
export function useProjectEditor(id: string | undefined, initialNotice = '') {
  const [load, setLoad] = useState<EditorLoad>(id ? { status: 'loading' } : { status: 'ready' })
  const [project, setProject] = useState<AdminProject | null>(null)
  const [draft, setDraft] = useState<ProjectDraft>(EMPTY_DRAFT)
  const [saved, setSaved] = useState<ProjectDraft>(EMPTY_DRAFT)
  // Un proyecto existente conserva su slug: cambiar el título no debe cambiar su dirección.
  const slugTouched = useRef(Boolean(id))
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState(initialNotice)
  const { expire } = useSession()
  const navigate = useNavigate()
  // Se lee en el momento de navegar (no en el render): así la navegación tras crear puede saltarse el aviso.
  const guard = useRef(false)
  const blocker = useBlocker(useCallback(() => guard.current, []))

  const notify = useCallback((message: string) => {
    setNotice((previous) => (previous === message ? `${message} ` : message))
  }, [])

  const apply = useCallback((next: AdminProject) => {
    const nextDraft = fromProject(next)
    setProject(next)
    setDraft(nextDraft)
    setSaved(nextDraft)
  }, [])

  const handleLoadError = useCallback((error: unknown) => {
    if (error instanceof ApiError && error.status === 401) {
      expire()
    } else if (error instanceof ApiError && error.status === 404) {
      setLoad({ status: 'notFound' })
    } else {
      setLoad({ status: 'error', message: error instanceof ApiError ? error.message : 'No se pudo cargar el proyecto.' })
    }
  }, [expire])

  useEffect(() => {
    if (!id) return undefined
    const controller = new AbortController()
    portfolioApi.fetchProject(id, controller.signal)
      .then((loaded) => {
        if (controller.signal.aborted) return
        apply(loaded)
        setLoad({ status: 'ready' })
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) handleLoadError(error)
      })
    return () => controller.abort()
  }, [id, apply, handleLoadError])

  /** Vuelve a leer el proyecto del servidor y descarta el borrador local (tras un 409). */
  async function reloadFromServer() {
    if (!id) return
    try {
      apply(await portfolioApi.fetchProject(id))
      setFieldErrors({})
    } catch (error) {
      handleLoadError(error)
    }
  }

  async function write(action: () => Promise<AdminProject>, success: string, onDone?: (result: AdminProject) => void) {
    setBusy(true)
    try {
      const result = await action()
      setFieldErrors({})
      notify(success)
      if (onDone) onDone(result)
      else apply(result)
    } catch (error) {
      if (!(error instanceof ApiError)) {
        notify('No se pudo guardar.')
      } else if (error.status === 401) {
        expire()
      } else if (error.status === 400 && Object.keys(error.fieldErrors).length > 0) {
        const mapped = mapFieldErrors(error.fieldErrors)
        setFieldErrors(mapped)
        const others = Object.entries(mapped).filter(([key]) => !SHOWN.test(key)).map(([, message]) => message)
        notify(['Revisa los campos marcados.', ...others].join(' '))
      } else if (error.code === 'MAX_ITEMS_REACHED') {
        // Crear solo puede chocar con el máximo de proyectos; guardar uno existente, con el de imágenes.
        notify(!project ? MAX_PROJECTS_MESSAGE : MAX_IMAGES_MESSAGE)
      } else if (error.status === 409) {
        notify(`${error.message} ${RELOADED}`)
        await reloadFromServer()
      } else if (error.status === 404) {
        setLoad({ status: 'notFound' })
      } else {
        notify(error.message)
      }
    } finally {
      setBusy(false)
    }
  }

  function save() {
    if (!draft.cover.mediaId) {
      setFieldErrors({ 'cover.mediaId': 'Elige una portada' })
      notify('Revisa los campos marcados.')
      return Promise.resolve()
    }
    const values = toValues(draft)
    if (!project) {
      return write(() => portfolioApi.createProject(values), 'Borrador guardado', (created) => {
        guard.current = false // ya está guardado: el editor se remonta con el proyecto creado
        void navigate(`/admin/portafolio/${created.id}`, { replace: true, state: { notice: 'Borrador guardado' } })
      })
    }
    return write(() => portfolioApi.updateProject(project.id, values, project.version), 'Borrador guardado')
  }

  function publish() {
    if (!project) return Promise.resolve()
    return write(() => portfolioApi.publishProject(project.id, project.version),
      project.hidden ? 'Proyecto retirado del sitio' : 'Proyecto publicado')
  }

  function update(patch: Partial<ProjectDraft>) {
    setDraft((current) => ({ ...current, ...patch }))
  }

  function setTitle(title: string) {
    setDraft((current) => ({ ...current, title, slug: slugTouched.current ? current.slug : slugify(title) }))
  }

  function setSlug(slug: string) {
    slugTouched.current = true
    update({ slug })
  }

  const dirty = JSON.stringify(toValues(draft)) !== JSON.stringify(toValues(saved))
  // Solo se avisa con un editor cargado: si el proyecto ya no existe no hay nada que guardar.
  const unsaved = dirty && load.status === 'ready'

  useEffect(() => {
    guard.current = unsaved
    if (!unsaved) return undefined
    // Recarga, cierre o salida a otro sitio: aviso nativo del navegador (no admite texto propio).
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [unsaved])

  return {
    load, project, draft, dirty, blocker, fieldErrors, setFieldErrors, busy, notice, save, publish, update, setTitle, setSlug,
  }
}
