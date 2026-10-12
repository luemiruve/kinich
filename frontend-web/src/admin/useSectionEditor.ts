import { useEffect, useRef, useState } from 'react'

import { ApiError } from '@/api/http'
import { useSession } from '@/session/useSession'

export type Editing<T> = { mode: 'create' } | { mode: 'edit'; target: T } | null

interface SectionEditorOptions {
  sectionKey: string
  reload: () => Promise<void>
  notify: (message: string) => void
  /** Avisa a la página de que esta sección tiene un formulario abierto (bloquea el cambio de modo). */
  onFormOpenChange: (sectionKey: string, open: boolean) => void
}

/**
 * Lógica común de las secciones editables (textos y elementos audiovisuales): formulario abierto, escrituras con
 * gestión de 401/400/409, aviso a la página y devolución del foco al cerrar.
 */
export function useSectionEditor<T extends { id: string }>({ sectionKey, reload, notify, onFormOpenChange }: SectionEditorOptions) {
  const [editing, setEditing] = useState<Editing<T>>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const { expire } = useSession()
  const root = useRef<HTMLElement>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  // Disparador del formulario abierto: el id del elemento editado o null para «Nuevo elemento».
  const trigger = useRef<string | null>(null)
  const wasOpen = useRef(false)
  const formOpen = editing !== null

  useEffect(() => {
    onFormOpenChange(sectionKey, formOpen)
    return () => onFormOpenChange(sectionKey, false)
  }, [formOpen, sectionKey, onFormOpenChange])

  // Al cerrar el formulario el foco vuelve al disparador; si ya no existe, al encabezado de la sección.
  useEffect(() => {
    if (formOpen) {
      wasOpen.current = true
      return
    }
    if (!wasOpen.current) return
    wasOpen.current = false
    const selector = trigger.current === null ? '[data-new-item]' : `[data-edit-id="${trigger.current}"]`
    const target = root.current?.querySelector<HTMLElement>(selector)
    target?.focus()
    if (!target || document.activeElement !== target) heading.current?.focus()
  }, [formOpen])

  /** Ejecuta una escritura, avisa, gestiona 400/409 y recarga el borrador. */
  async function run(action: () => Promise<unknown>, success: string): Promise<boolean> {
    setBusy(true)
    try {
      await action()
      notify(success)
      await reload()
      return true
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        expire()
        return false
      }
      if (error instanceof ApiError && error.status === 400 && Object.keys(error.fieldErrors).length > 0) {
        setFieldErrors(error.fieldErrors)
        return false
      }
      notify(error instanceof ApiError ? error.message : 'No se pudo guardar.')
      if (error instanceof ApiError && error.code === 'VERSION_CONFLICT') {
        // El formulario abierto conserva una versión obsoleta: se cierra y se recarga el borrador.
        await reload()
        setEditing(null)
      }
      return false
    } finally {
      setBusy(false)
    }
  }

  function startCreate() {
    setFieldErrors({})
    trigger.current = null
    setEditing({ mode: 'create' })
  }

  function startEdit(target: T) {
    setFieldErrors({})
    trigger.current = target.id
    setEditing({ mode: 'edit', target })
  }

  return {
    editing, formOpen, busy, fieldErrors, setFieldErrors, run, startCreate, startEdit,
    close: () => setEditing(null), root, heading,
  }
}
