import { useEffect, useRef } from 'react'

import { Button } from '@/components/Button'

/**
 * Confirmación de borrado físico (D5) con <dialog> nativo: foco atrapado y Escape gratis.
 * Se monta solo mientras se confirma; al montarse se abre como modal. `title`, `confirmLabel` y `cancelLabel`
 * permiten reutilizarlo para otras confirmaciones (p. ej. salir sin guardar); por omisión, el texto de borrado.
 */
export function ConfirmDeleteDialog({
  label, onConfirm, onCancel,
  description = 'Se borra del borrador. Seguirá visible en el sitio hasta que publiques la sección.',
  title = `¿Eliminar «${label}»?`, confirmLabel = 'Eliminar definitivamente', cancelLabel = 'Cancelar',
}: {
  label?: string; onConfirm: () => void; onCancel: () => void; description?: string
  title?: string; confirmLabel?: string; cancelLabel?: string
}) {
  const dialog = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    // El diálogo se desmonta abierto: se devuelve el foco a quien lo invocó.
    const previous = document.activeElement as HTMLElement | null
    dialog.current?.showModal()
    return () => previous?.focus()
  }, [])

  return (
    <dialog ref={dialog} role="alertdialog" aria-labelledby="confirm-delete-title"
      aria-describedby="confirm-delete-description" onCancel={(event) => { event.preventDefault(); onCancel() }}
      className="m-auto max-w-sm border border-outline bg-surface p-6 text-on-surface backdrop:bg-feature-panel/60">
      <h2 id="confirm-delete-title" className="font-serif text-xl">{title}</h2>
      <p id="confirm-delete-description" className="mt-2 text-on-surface-variant">{description}</p>
      <div className="mt-6 flex flex-wrap justify-end gap-2">
        <Button variant="text" onClick={onCancel}>{cancelLabel}</Button>
        <Button className="w-auto" onClick={onConfirm}>{confirmLabel}</Button>
      </div>
    </dialog>
  )
}
