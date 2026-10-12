import { useEffect, useId, useRef, useState, type FormEvent } from 'react'

import type { TextBlockValues } from '@/api/adminPage'
import { Button } from '@/components/Button'
import { TextField } from '@/components/form/TextField'
import { inputClassName, labelClassName } from '@/components/form/fieldStyles'
import { TEXT_LIMITS } from '@/content/textLimits'

interface TextBlockFormProps {
  initial?: TextBlockValues
  fieldErrors: Record<string, string>
  onSubmit: (values: TextBlockValues) => Promise<void>
  onCancel: () => void
}

export function TextBlockForm({ initial, fieldErrors, onSubmit, onCancel }: TextBlockFormProps) {
  const [values, setValues] = useState<TextBlockValues>(initial ?? { eyebrow: '', title: '', body: '' })
  const [saving, setSaving] = useState(false)
  const form = useRef<HTMLFormElement>(null)
  const bodyId = useId()
  const counterId = useId()
  const errorId = useId()
  const eyebrowErrorId = useId()
  const titleErrorId = useId()

  // Foco gestionado al abrir (WCAG 2.4.3): el primer campo.
  useEffect(() => {
    form.current?.querySelector<HTMLElement>('input, textarea')?.focus()
  }, [])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    try {
      await onSubmit(values)
    } finally {
      setSaving(false)
    }
  }

  const set = (field: keyof TextBlockValues) => (value: string) => setValues((v) => ({ ...v, [field]: value }))

  return (
    <form ref={form} onSubmit={(e) => void handleSubmit(e)} className="flex flex-col gap-4 border border-outline-variant bg-surface-lowest p-4">
      <TextField label="Encabezado (opcional)" value={values.eyebrow} maxLength={TEXT_LIMITS.eyebrow}
        invalid={!!fieldErrors.eyebrow} describedBy={fieldErrors.eyebrow ? eyebrowErrorId : undefined} onChange={(e) => set('eyebrow')(e.target.value)} />
      {fieldErrors.eyebrow && <p id={eyebrowErrorId} className="text-sm text-error">{fieldErrors.eyebrow}</p>}
      <TextField label="Título (opcional)" value={values.title} maxLength={TEXT_LIMITS.title}
        invalid={!!fieldErrors.title} describedBy={fieldErrors.title ? titleErrorId : undefined} onChange={(e) => set('title')(e.target.value)} />
      {fieldErrors.title && <p id={titleErrorId} className="text-sm text-error">{fieldErrors.title}</p>}
      <div className="flex flex-col gap-1.5">
        <label htmlFor={bodyId} className={labelClassName}>Texto</label>
        <textarea
          id={bodyId}
          required
          rows={5}
          maxLength={TEXT_LIMITS.body}
          value={values.body}
          aria-invalid={fieldErrors.body ? true : undefined}
          aria-describedby={fieldErrors.body ? `${counterId} ${errorId}` : counterId}
          onChange={(e) => set('body')(e.target.value)}
          className={`${inputClassName} h-auto py-3`}
        />
        {/* String.length cuenta unidades UTF-16, igual que @Size en el backend. */}
        <p id={counterId} className="text-right text-sm text-on-surface-variant">
          {values.body.length} / {TEXT_LIMITS.body}
        </p>
        {fieldErrors.body && <p id={errorId} className="text-sm text-error">{fieldErrors.body}</p>}
      </div>
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="text" onClick={onCancel}>Cancelar</Button>
        <Button type="submit" className="w-auto" disabled={saving}>Guardar borrador</Button>
      </div>
    </form>
  )
}
