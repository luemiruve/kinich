import { useId, useState } from 'react'

import type { MediaItem } from '@/api/adminMedia'
import { Button } from '@/components/Button'
import { TextField } from '@/components/form/TextField'
import { PORTFOLIO_LIMITS } from '@/content/textLimits'

import { FramingEditor } from '../FramingEditor'
import { MediaPicker } from '../MediaPicker'
import type { ProjectDraft } from './useProjectEditor'

interface CoverFieldProps {
  cover: ProjectDraft['cover']
  fieldErrors: Record<string, string>
  onChange: (cover: ProjectDraft['cover']) => void
}

/** Portada de la tarjeta: imagen, encuadre 4:5 (no modifica el original) y texto alternativo. */
export function CoverField({ cover, fieldErrors, onChange }: CoverFieldProps) {
  const [picking, setPicking] = useState(false)
  const mediaErrorId = useId()
  const cropErrorId = useId()
  const altErrorId = useId()
  // El error del encuadre se asocia al botón de la portada: el editor de encuadre no expone un control propio.
  const pickerDescribedBy = [
    fieldErrors['cover.mediaId'] && mediaErrorId,
    fieldErrors['cover.crop'] && cropErrorId,
  ].filter(Boolean).join(' ') || undefined

  function choose(media: MediaItem) {
    setPicking(false)
    // Otra foto: el encuadre vuelve al centrado por defecto y el alt se vacía (describía la anterior).
    const alt = media.id === cover.mediaId ? cover.alt : ''
    onChange({ ...cover, mediaId: media.id, alt, crop: null, image: { width: media.width, height: media.height, ...media.renditions } })
  }

  return (
    <fieldset className="flex flex-col gap-4">
      <legend className="font-serif text-2xl">Portada</legend>
      <Button variant="text" className="self-start" aria-describedby={pickerDescribedBy}
        onClick={() => setPicking(true)}>
        {cover.mediaId ? 'Cambiar portada' : 'Elegir portada'}
      </Button>
      {fieldErrors['cover.mediaId'] && <p id={mediaErrorId} className="text-sm text-error">{fieldErrors['cover.mediaId']}</p>}
      {cover.image && (
        <FramingEditor key={cover.mediaId} framing="CROP_4_5" image={cover.image} crop={cover.crop} mediaPosition={null}
          onChange={({ crop }) => onChange({ ...cover, crop })} />
      )}
      {fieldErrors['cover.crop'] && <p id={cropErrorId} className="text-sm text-error">{fieldErrors['cover.crop']}</p>}
      <div className="flex flex-col gap-1.5">
        <TextField label="Texto alternativo de la portada (describe la imagen)" value={cover.alt} required
          maxLength={PORTFOLIO_LIMITS.alt} invalid={!!fieldErrors['cover.alt']}
          describedBy={fieldErrors['cover.alt'] ? altErrorId : undefined}
          onChange={(e) => onChange({ ...cover, alt: e.target.value })} />
        {fieldErrors['cover.alt'] && <p id={altErrorId} className="text-sm text-error">{fieldErrors['cover.alt']}</p>}
      </div>
      {picking && <MediaPicker onSelect={choose} onClose={() => setPicking(false)} />}
    </fieldset>
  )
}
