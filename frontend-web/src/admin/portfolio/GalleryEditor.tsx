import { useId, useLayoutEffect, useRef, useState } from 'react'

import type { MediaItem } from '@/api/adminMedia'
import { Button } from '@/components/Button'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { TextField } from '@/components/form/TextField'
import { IMAGE_SIZES } from '@/content/imageSizes'
import { PORTFOLIO_LIMITS } from '@/content/textLimits'

import { MediaPicker } from '../MediaPicker'
import { newEntryKey, type GalleryEntry } from './useProjectEditor'

interface GalleryEditorProps {
  images: GalleryEntry[]
  fieldErrors: Record<string, string>
  onChange: (images: GalleryEntry[]) => void
  /** Reordenar, añadir o quitar cambia los índices: los errores por imagen dejan de corresponder. */
  onStructureChange: () => void
}

type Direction = 'up' | 'down'

/** Galería ordenada (1–60 imágenes): añadir desde el selector, alt y pie por imagen, subir/bajar/quitar. */
export function GalleryEditor({ images, fieldErrors, onChange, onStructureChange }: GalleryEditorProps) {
  const [picking, setPicking] = useState(false)
  const counterId = useId()
  const imagesErrorId = useId()
  const listRef = useRef<HTMLOListElement>(null)
  const rootRef = useRef<HTMLFieldSetElement>(null)
  // Tras mover, el foco sigue a la imagen movida (mismo botón, o el contrario si llegó a un extremo).
  const pendingFocus = useRef<{ key: string; direction: Direction } | 'add' | null>(null)
  const full = images.length >= PORTFOLIO_LIMITS.images

  useLayoutEffect(() => {
    const target = pendingFocus.current
    if (!target) return
    pendingFocus.current = null
    if (target === 'add') {
      rootRef.current?.querySelector<HTMLElement>('[data-gallery-add]')?.focus()
      return
    }
    const other = target.direction === 'up' ? 'down' : 'up'
    const button = listRef.current?.querySelector<HTMLElement>(`[data-gallery-move="${target.key}:${target.direction}"]`)
      ?? listRef.current?.querySelector<HTMLElement>(`[data-gallery-move="${target.key}:${other}"]`)
    button?.focus()
  }, [images])

  function edit(index: number, patch: Partial<GalleryEntry>) {
    onChange(images.map((entry, i) => (i === index ? { ...entry, ...patch } : entry)))
  }

  function move(index: number, direction: Direction) {
    const next = [...images]
    const target = index + (direction === 'up' ? -1 : 1)
    ;[next[index], next[target]] = [next[target], next[index]]
    pendingFocus.current = { key: images[index].key, direction }
    onStructureChange()
    onChange(next)
  }

  function remove(index: number) {
    pendingFocus.current = 'add'
    onStructureChange()
    onChange(images.filter((_, i) => i !== index))
  }

  function add(media: MediaItem) {
    setPicking(false)
    onStructureChange()
    onChange([...images, {
      key: newEntryKey(), mediaId: media.id, alt: '', caption: '',
      image: { width: media.width, height: media.height, ...media.renditions },
    }])
  }

  return (
    <fieldset ref={rootRef} className="flex flex-col gap-4">
      <legend className="font-serif text-2xl">Galería</legend>
      <p id={counterId} className="text-sm text-on-surface-variant">{images.length} de {PORTFOLIO_LIMITS.images}</p>
      {fieldErrors.images && <p id={imagesErrorId} className="text-sm text-error">{fieldErrors.images}</p>}
      <ol ref={listRef} className="flex flex-col gap-6">
        {images.map((entry, index) => (
          <GalleryItem key={entry.key} entry={entry} index={index} count={images.length} fieldErrors={fieldErrors}
            onEdit={(patch) => edit(index, patch)} onMove={(direction) => move(index, direction)} onRemove={() => remove(index)} />
        ))}
      </ol>
      <Button data-gallery-add variant="text" className="self-start" disabled={full} aria-describedby={fieldErrors.images ? `${counterId} ${imagesErrorId}` : counterId}
        onClick={() => setPicking(true)}>
        Añadir imagen
      </Button>
      {picking && <MediaPicker onSelect={add} onClose={() => setPicking(false)} />}
    </fieldset>
  )
}

interface GalleryItemProps {
  entry: GalleryEntry
  index: number
  count: number
  fieldErrors: Record<string, string>
  onEdit: (patch: Partial<GalleryEntry>) => void
  onMove: (direction: Direction) => void
  onRemove: () => void
}

function GalleryItem({ entry, index, count, fieldErrors, onEdit, onMove, onRemove }: GalleryItemProps) {
  const n = index + 1
  const prefix = `images[${index}]`
  const ids = { media: useId(), alt: useId(), caption: useId() }
  const mediaError = fieldErrors[`${prefix}.mediaId`] ?? fieldErrors[prefix]
  const altError = fieldErrors[`${prefix}.alt`]
  const captionError = fieldErrors[`${prefix}.caption`]
  return (
    <li className="flex flex-wrap gap-4 border-t border-outline-variant pt-4">
      {/* Decorativa: el alt se edita al lado. Proporción original, como en el detalle público. */}
      <MediaFrame image={{ ...entry.image, alt: '', crop: null, mediaPosition: null }} sizes={IMAGE_SIZES.thumbnail}
        className="w-32 shrink-0" style={{ aspectRatio: `${entry.image.width} / ${entry.image.height}` }} />
      <div className="flex min-w-0 flex-1 flex-col gap-3">
        <p className="label-caps text-on-surface-variant">Imagen {n}</p>
        {mediaError && <p id={ids.media} className="text-sm text-error">{mediaError}</p>}
        <div className="flex flex-col gap-1.5">
          <TextField label={`Texto alternativo de la imagen ${n}`} value={entry.alt} required maxLength={PORTFOLIO_LIMITS.alt}
            invalid={!!altError}
            describedBy={[mediaError && ids.media, altError && ids.alt].filter(Boolean).join(' ') || undefined}
            onChange={(e) => onEdit({ alt: e.target.value })} />
          {altError && <p id={ids.alt} className="text-sm text-error">{altError}</p>}
        </div>
        <div className="flex flex-col gap-1.5">
          <TextField label={`Pie de foto de la imagen ${n} (opcional)`} value={entry.caption} maxLength={PORTFOLIO_LIMITS.caption}
            invalid={!!captionError} describedBy={captionError ? ids.caption : undefined}
            onChange={(e) => onEdit({ caption: e.target.value })} />
          {captionError && <p id={ids.caption} className="text-sm text-error">{captionError}</p>}
        </div>
        <div className="flex flex-wrap gap-1">
          {index > 0 && (
            <Button variant="text" className="min-w-11" data-gallery-move={`${entry.key}:up`} aria-label={`Subir imagen ${n}`}
              onClick={() => onMove('up')}>↑</Button>
          )}
          {index < count - 1 && (
            <Button variant="text" className="min-w-11" data-gallery-move={`${entry.key}:down`} aria-label={`Bajar imagen ${n}`}
              onClick={() => onMove('down')}>↓</Button>
          )}
          <Button variant="text" aria-label={`Quitar imagen ${n}`} onClick={onRemove}>Quitar</Button>
        </div>
      </div>
    </li>
  )
}
