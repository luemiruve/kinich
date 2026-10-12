import { useEffect, useId, useRef, useState, type ChangeEvent } from 'react'

import { fetchMedia, uploadMedia, type MediaItem } from '@/api/adminMedia'
import { ApiError } from '@/api/http'
import { Button } from '@/components/Button'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { IMAGE_SIZES } from '@/content/imageSizes'
import { MEDIA_LIMITS } from '@/content/mediaLimits'

const dateFormat = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' })

/**
 * Selector de imágenes subidas + subida, en un <dialog> nativo (foco atrapado y Escape).
 * La validación del cliente solo ayuda: la autoridad es el servidor.
 */
export function MediaPicker({ onSelect, onClose }: { onSelect: (media: MediaItem) => void; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const inputId = useId()
  const titleId = useId()
  const [media, setMedia] = useState<MediaItem[] | null>(null)
  const [error, setError] = useState('')
  const [uploading, setUploading] = useState(false)
  // Quien abrió el selector, capturado al renderizar (antes de showModal): con StrictMode el efecto se repite y,
  // para entonces, el foco ya está dentro del diálogo.
  const [opener] = useState(() => document.activeElement as HTMLElement | null)

  useEffect(() => {
    const element = dialog.current
    if (element && !element.open) element.showModal()
    const controller = new AbortController()
    fetchMedia(controller.signal).then(setMedia).catch(() => setMedia([]))
    return () => {
      controller.abort()
      // El diálogo se desmonta abierto: se devuelve el foco a quien lo invocó.
      opener?.focus()
    }
  }, [opener])

  async function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setError('')
    if (file.size > MEDIA_LIMITS.maxBytes) {
      setError('El archivo supera el máximo de 10 MB.')
      return
    }
    if (!(MEDIA_LIMITS.acceptedTypes as readonly string[]).includes(file.type)) {
      setError('Formato no permitido: usa JPEG, PNG, WebP o AVIF.')
      return
    }
    setUploading(true)
    try {
      onSelect(await uploadMedia(file))
    } catch (failure) {
      setError(failure instanceof ApiError ? (failure.fieldErrors.file ?? failure.message) : 'No se pudo subir la imagen.')
    } finally {
      setUploading(false)
    }
  }

  return (
    <dialog ref={dialog} aria-labelledby={titleId} onCancel={(event) => { event.preventDefault(); onClose() }}
      className="m-auto w-[min(56rem,calc(100%-2rem))] border border-outline bg-surface p-6 text-on-surface backdrop:bg-feature-panel/60">
      <h2 id={titleId} className="font-serif text-xl">Elegir imagen</h2>
      <p className="mt-2 text-sm text-on-surface-variant">
        Sube una exportación para web: JPEG, PNG, WebP o AVIF, máximo 10 MB y {MEDIA_LIMITS.maxDimension} px por lado.
        Se eliminan los metadatos (EXIF/GPS) antes de guardarla; las fotos con rotación pendiente se rechazan.
      </p>
      <div className="mt-4 flex flex-col gap-1.5">
        <label htmlFor={inputId} className="label-caps">Subir imagen</label>
        <input id={inputId} type="file" accept={MEDIA_LIMITS.acceptedTypes.join(',')} disabled={uploading}
          onChange={(event) => void handleFile(event)} className="min-h-11" />
      </div>
      {uploading && <p role="status" className="mt-2 text-sm">Subiendo…</p>}
      {error && <p role="alert" className="mt-2 text-sm text-error">{error}</p>}
      {media === null ? (
        <p role="status" className="mt-6">Cargando imágenes…</p>
      ) : (
        <ul className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
          {media.map((item) => (
            <li key={item.id}>
              <button type="button" onClick={() => onSelect(item)}
                aria-label={`Imagen ${item.width} × ${item.height}, subida el ${dateFormat.format(new Date(item.createdAt))}`}
                className="block min-h-11 w-full cursor-pointer border border-outline-variant hover:border-on-surface">
                {/* Decorativa: el botón ya tiene nombre accesible. Solo representaciones web, nunca el original. */}
                <MediaFrame image={{ alt: '', width: item.width, height: item.height, ...item.renditions, crop: null,
                  mediaPosition: null }} sizes={IMAGE_SIZES.picker} className="aspect-square w-full" />
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="mt-6 flex justify-end">
        <Button variant="text" onClick={onClose}>Cancelar</Button>
      </div>
    </dialog>
  )
}
