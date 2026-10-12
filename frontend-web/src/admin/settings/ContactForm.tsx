import { useId, useState, type FormEvent } from 'react'

import { updateContact, type AdminSettings, type ContactValues } from '@/api/adminSettings'
import { Button } from '@/components/Button'
import { TextField } from '@/components/form/TextField'
import { inputClassName, labelClassName } from '@/components/form/fieldStyles'
import { SETTINGS_LIMITS } from '@/content/site'

import { FieldError } from './FieldError'
import type { useSettings } from './useSettings'

type Run = ReturnType<typeof useSettings>['run']
type Field = keyof ContactValues

const PHONE_HINT = 'Formato internacional: + código de país y número.'

/** Contacto: todos los campos son opcionales; lo vacío no se muestra en el sitio. */
export function ContactForm({ settings, run }: { settings: AdminSettings; run: Run }) {
  const c = settings.contact
  const [values, setValues] = useState<ContactValues>({
    email: c.email ?? '', whatsapp: c.whatsapp ?? '', phone: c.phone ?? '', location: c.location ?? '',
    address: c.address ?? '', openingHours: c.openingHours ?? '',
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const ids: Record<Field | 'phoneHint' | 'addressInput' | 'hoursInput', string> = {
    email: useId(), whatsapp: useId(), phone: useId(), location: useId(), address: useId(), openingHours: useId(),
    phoneHint: useId(), addressInput: useId(), hoursInput: useId(),
  }

  const set = (field: Field) => (value: string) => setValues((v) => ({ ...v, [field]: value }))
  const describedBy = (field: Field, hint?: string) => [hint, errors[field] ? ids[field] : null].filter(Boolean).join(' ') || undefined

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    await run(() => updateContact(values, settings.version), 'Contacto guardado', setErrors)
    setSaving(false)
  }

  return (
    <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-4">
      <p id={ids.phoneHint} className="text-sm text-on-surface-variant">{PHONE_HINT}</p>
      <TextField label="Correo electrónico" type="email" autoComplete="email" value={values.email} maxLength={SETTINGS_LIMITS.email}
        invalid={!!errors.email} describedBy={describedBy('email')} onChange={(e) => set('email')(e.target.value)} />
      <FieldError id={ids.email} message={errors.email} />
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <TextField label="WhatsApp" type="tel" autoComplete="tel" value={values.whatsapp} maxLength={SETTINGS_LIMITS.phone}
            invalid={!!errors.whatsapp} describedBy={describedBy('whatsapp', ids.phoneHint)} onChange={(e) => set('whatsapp')(e.target.value)} />
          <FieldError id={ids.whatsapp} message={errors.whatsapp} />
        </div>
        <div className="flex flex-col gap-1.5">
          <TextField label="Teléfono" type="tel" autoComplete="tel" value={values.phone} maxLength={SETTINGS_LIMITS.phone}
            invalid={!!errors.phone} describedBy={describedBy('phone', ids.phoneHint)} onChange={(e) => set('phone')(e.target.value)} />
          <FieldError id={ids.phone} message={errors.phone} />
        </div>
      </div>
      <TextField label="Ciudad o ubicación" value={values.location} maxLength={SETTINGS_LIMITS.location}
        invalid={!!errors.location} describedBy={describedBy('location')} onChange={(e) => set('location')(e.target.value)} />
      <FieldError id={ids.location} message={errors.location} />
      <div className="flex flex-col gap-1.5">
        <label htmlFor={ids.addressInput} className={labelClassName}>Dirección</label>
        <textarea id={ids.addressInput} rows={3} maxLength={SETTINGS_LIMITS.address} value={values.address}
          aria-invalid={errors.address ? true : undefined} aria-describedby={describedBy('address')}
          onChange={(e) => set('address')(e.target.value)} className={`${inputClassName} h-auto py-3`} />
        <FieldError id={ids.address} message={errors.address} />
      </div>
      <div className="flex flex-col gap-1.5">
        <label htmlFor={ids.hoursInput} className={labelClassName}>Horarios</label>
        <textarea id={ids.hoursInput} rows={3} maxLength={SETTINGS_LIMITS.openingHours} value={values.openingHours}
          aria-invalid={errors.openingHours ? true : undefined} aria-describedby={describedBy('openingHours')}
          onChange={(e) => set('openingHours')(e.target.value)} className={`${inputClassName} h-auto py-3`} />
        <FieldError id={ids.openingHours} message={errors.openingHours} />
      </div>
      <Button type="submit" className="w-auto self-end" disabled={saving}>Guardar contacto</Button>
    </form>
  )
}
