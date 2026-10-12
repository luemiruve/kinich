import { useEffect, useId, useRef, useState, type FormEvent } from 'react'

import type { MediaItem } from '@/api/adminMedia'
import type { ItemFieldName, ItemFraming, ItemValues } from '@/api/adminPage'
import { Button } from '@/components/Button'
import { TextField } from '@/components/form/TextField'
import { inputClassName, labelClassName } from '@/components/form/fieldStyles'
import { MEDIA_LIMITS } from '@/content/mediaLimits'
import { TEXT_LIMITS } from '@/content/textLimits'

import { CategorySelect } from './CategorySelect'
import { FramingEditor, type FramingImage } from './FramingEditor'
import { MediaPicker } from './MediaPicker'

interface ContentItemFormProps {
  initial?: ItemValues
  /** Imagen actual del elemento editado: dimensiones del original y representaciones (vista previa del encuadre). */
  initialImage?: FramingImage
  /** Tipo de encuadre de la sección (rectángulo 4:5 o punto focal). */
  framing: ItemFraming
  /** Campos opcionales que admite la sección y cuáles son obligatorios (vienen del backend). */
  fields: ItemFieldName[]
  required: ItemFieldName[]
  fieldErrors: Record<string, string>
  onSubmit: (values: ItemValues) => Promise<void>
  onCancel: () => void
}

const EMPTY: ItemValues = {
  mediaId: '', imageAlt: '', categoryId: null, eyebrow: '', title: '', description: '', crop: null, mediaPosition: null,
}

/** Formulario reutilizable de un elemento audiovisual: imagen + texto alternativo + los campos de la sección. */
export function ContentItemForm({ initial, initialImage, framing, fields, required, fieldErrors, onSubmit, onCancel }: ContentItemFormProps) {
  const [values, setValues] = useState<ItemValues>(initial ?? EMPTY)
  const [image, setImage] = useState<FramingImage | null>(initialImage ?? null)
  const [picking, setPicking] = useState(false)
  const [missingImage, setMissingImage] = useState(false)
  const [saving, setSaving] = useState(false)
  const form = useRef<HTMLFormElement>(null)
  const imageErrorId = useId()
  const altErrorId = useId()
  const eyebrowErrorId = useId()
  const titleErrorId = useId()
  const descriptionId = useId()
  const counterId = useId()
  const descriptionErrorId = useId()

  // Foco gestionado al abrir (WCAG 2.4.3): el primer control.
  useEffect(() => {
    form.current?.querySelector<HTMLElement>('button, input, select, textarea')?.focus()
  }, [])

  const set = (field: 'imageAlt' | 'categoryId' | 'eyebrow' | 'title' | 'description') => (value: string | null) =>
    setValues((v) => ({ ...v, [field]: value }))
  const has = (field: ItemFieldName) => fields.includes(field)
  const imageError = missingImage ? 'Elige una imagen' : fieldErrors.mediaId

  function choose(media: MediaItem) {
    setPicking(false)
    setMissingImage(false)
    // Otra foto: el encuadre vuelve al centrado por defecto.
    setValues((v) => ({ ...v, mediaId: media.id, crop: null, mediaPosition: null }))
    setImage({ width: media.width, height: media.height, ...media.renditions })
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!values.mediaId) {
      setMissingImage(true)
      return
    }
    setSaving(true)
    try {
      await onSubmit(values)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form ref={form} onSubmit={(e) => void handleSubmit(e)} className="flex flex-col gap-4 border border-outline-variant bg-surface-lowest p-4">
      <Button variant="text" className="self-start" aria-describedby={imageError ? imageErrorId : undefined}
        onClick={() => setPicking(true)}>
        {values.mediaId ? 'Cambiar imagen' : 'Elegir imagen'}
      </Button>
      {imageError && <p id={imageErrorId} className="text-sm text-error">{imageError}</p>}
      {image && (
        <FramingEditor key={values.mediaId} framing={framing} image={image} crop={values.crop}
          mediaPosition={values.mediaPosition}
          onChange={({ crop, mediaPosition }) => setValues((v) => ({ ...v, crop, mediaPosition }))} />
      )}
      {(fieldErrors.crop || fieldErrors.mediaPosition) && (
        <p className="text-sm text-error">{fieldErrors.crop ?? fieldErrors.mediaPosition}</p>
      )}
      <TextField label="Texto alternativo (describe la imagen)" value={values.imageAlt} required maxLength={MEDIA_LIMITS.imageAlt}
        invalid={!!fieldErrors.imageAlt} describedBy={fieldErrors.imageAlt ? altErrorId : undefined}
        onChange={(e) => set('imageAlt')(e.target.value)} />
      {fieldErrors.imageAlt && <p id={altErrorId} className="text-sm text-error">{fieldErrors.imageAlt}</p>}
      {has('categoryId') && (
        <CategorySelect value={values.categoryId} onChange={set('categoryId')} error={fieldErrors.categoryId} />
      )}
      {has('eyebrow') && (
        <>
          <TextField label="Encabezado (opcional)" value={values.eyebrow} maxLength={TEXT_LIMITS.eyebrow}
            invalid={!!fieldErrors.eyebrow} describedBy={fieldErrors.eyebrow ? eyebrowErrorId : undefined}
            onChange={(e) => set('eyebrow')(e.target.value)} />
          {fieldErrors.eyebrow && <p id={eyebrowErrorId} className="text-sm text-error">{fieldErrors.eyebrow}</p>}
        </>
      )}
      {has('title') && (
        <>
          <TextField label={required.includes('title') ? 'Título' : 'Título (opcional)'} value={values.title}
            required={required.includes('title')} maxLength={TEXT_LIMITS.title}
            invalid={!!fieldErrors.title} describedBy={fieldErrors.title ? titleErrorId : undefined}
            onChange={(e) => set('title')(e.target.value)} />
          {fieldErrors.title && <p id={titleErrorId} className="text-sm text-error">{fieldErrors.title}</p>}
        </>
      )}
      {has('description') && (
        <div className="flex flex-col gap-1.5">
          <label htmlFor={descriptionId} className={labelClassName}>Descripción (opcional)</label>
          <textarea
            id={descriptionId}
            rows={4}
            maxLength={MEDIA_LIMITS.description}
            value={values.description}
            aria-invalid={fieldErrors.description ? true : undefined}
            aria-describedby={fieldErrors.description ? `${counterId} ${descriptionErrorId}` : counterId}
            onChange={(e) => set('description')(e.target.value)}
            className={`${inputClassName} h-auto py-3`}
          />
          {/* String.length cuenta unidades UTF-16, igual que @Size en el backend. */}
          <p id={counterId} className="text-right text-sm text-on-surface-variant">
            {values.description.length} / {MEDIA_LIMITS.description}
          </p>
          {fieldErrors.description && <p id={descriptionErrorId} className="text-sm text-error">{fieldErrors.description}</p>}
        </div>
      )}
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="text" onClick={onCancel}>Cancelar</Button>
        <Button type="submit" className="w-auto" disabled={saving}>Guardar borrador</Button>
      </div>
      {picking && <MediaPicker onSelect={choose} onClose={() => setPicking(false)} />}
    </form>
  )
}
