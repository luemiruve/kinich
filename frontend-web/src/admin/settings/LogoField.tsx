import { useId, type ChangeEvent } from 'react'

import type { SiteLogo } from '@/api/site'
import { Logo } from '@/components/brand/Logo'
import { Button } from '@/components/Button'

interface LogoFieldProps {
  label: string
  /** Fondo de la vista previa: el del tema claro o un panel oscuro. */
  preview: 'light' | 'dark'
  name: string
  logo: SiteLogo | null
  /** Mientras no se pueda subir (otra operación en curso o, en la variante oscura, sin principal). */
  disabled: boolean
  hint?: string
  onUpload: (file: File) => void
  onRemove: () => void
  error?: string
}

/**
 * Una variante del logotipo: subir su SVG (se aplica al momento, el backend lo sanitiza) o quitarla. No usa la
 * biblioteca de medios: los logotipos son recursos de marca propios.
 */
export function LogoField({ label, preview, name, logo, disabled, hint, onUpload, onRemove, error }: LogoFieldProps) {
  const ids = { input: useId(), hint: useId(), error: useId() }
  const describedBy = [hint ? ids.hint : null, error ? ids.error : null].filter(Boolean).join(' ') || undefined

  function choose(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = '' // permite volver a elegir el mismo archivo tras un error
    if (file) onUpload(file)
  }

  return (
    <div className="flex flex-col gap-2" role="group" aria-label={label}>
      <p className="label-caps text-on-surface-variant">{label}</p>
      <div className={`flex min-h-20 items-center border border-outline-variant p-4 ${preview === 'dark' ? 'bg-feature-panel text-on-feature-panel' : 'bg-surface'}`}>
        {logo
          ? <Logo name={`Vista previa: ${name}`} logo={logo} logoOnDark={null} maxHeight={48} className="h-12" />
          : <span className="text-sm">Sin logotipo</span>}
      </div>
      {hint && <p id={ids.hint} className="text-sm text-on-surface-variant">{hint}</p>}
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor={ids.input} className="label-caps min-h-11 content-center">Subir SVG</label>
        <input id={ids.input} type="file" accept=".svg,image/svg+xml" disabled={disabled} onChange={choose}
          aria-describedby={describedBy} aria-invalid={error ? true : undefined}
          className="min-h-11 max-w-full text-sm file:mr-3 file:min-h-11 file:border file:border-outline file:bg-surface file:px-3" />
        {logo && <Button variant="text" disabled={disabled} onClick={onRemove}>Quitar</Button>}
      </div>
      {error && <p id={ids.error} className="text-sm text-error">{error}</p>}
    </div>
  )
}
