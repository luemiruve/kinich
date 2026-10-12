import { useId, useState, type FormEvent } from 'react'

import { updateIdentity, type AdminSettings } from '@/api/adminSettings'
import { Button } from '@/components/Button'
import { TextField } from '@/components/form/TextField'
import { SETTINGS_LIMITS } from '@/content/site'

import { FieldError } from './FieldError'
import type { useSettings } from './useSettings'

type Run = ReturnType<typeof useSettings>['run']

/** Identidad: nombre (obligatorio) y lema. Se aplica al guardar, sin publicación. Los logotipos van aparte. */
export function IdentityForm({ settings, run }: { settings: AdminSettings; run: Run }) {
  const [name, setName] = useState(settings.identity.businessName ?? '')
  const [tagline, setTagline] = useState(settings.identity.tagline ?? '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const ids = { name: useId(), tagline: useId(), hint: useId() }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    // settings.version es la vigente: una subida de logotipo previa ya la actualizó.
    await run(() => updateIdentity({ businessName: name, tagline }, settings.version), 'Identidad guardada', setErrors)
    setSaving(false)
  }

  return (
    <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-4" aria-describedby={ids.hint}>
      <p id={ids.hint} className="text-sm text-on-surface-variant">
        Se aplica en el sitio al guardar.
      </p>
      <TextField label="Nombre del negocio o estudio" value={name} required maxLength={SETTINGS_LIMITS.businessName}
        invalid={!!errors.businessName} describedBy={errors.businessName ? ids.name : undefined}
        onChange={(e) => setName(e.target.value)} />
      <FieldError id={ids.name} message={errors.businessName} />
      <TextField label="Lema (opcional)" value={tagline} maxLength={SETTINGS_LIMITS.tagline}
        invalid={!!errors.tagline} describedBy={errors.tagline ? ids.tagline : undefined}
        onChange={(e) => setTagline(e.target.value)} />
      <FieldError id={ids.tagline} message={errors.tagline} />
      <Button type="submit" className="w-auto self-end" disabled={saving}>Guardar identidad</Button>
    </form>
  )
}
