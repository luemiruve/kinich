import { useEffect, useId, useRef, useState, type FormEvent } from 'react'

import * as adminApi from '@/api/adminPage'
import type { AdminSection, HeadingFieldName, HeadingSectionKey, HeadingValues, PageKey } from '@/api/adminPage'
import { Button } from '@/components/Button'
import { TextField } from '@/components/form/TextField'
import { inputClassName, labelClassName } from '@/components/form/fieldStyles'

import { useSectionEditor } from './useSectionEditor'

/** Lo que el editor necesita de una sección, sea de Inicio o de Portafolio. */
type HeadingSection = Pick<AdminSection, 'headingFields' | 'heading' | 'revision'> & { sectionKey: HeadingSectionKey }

interface SectionHeadingEditorProps {
  section: HeadingSection
  /** Página de la sección; sin indicarla, Inicio (sus llamadas no cambian). */
  page?: PageKey
  reload: () => Promise<void>
  notify: (message: string) => void
  onFormOpenChange: (sectionKey: string, open: boolean) => void
  /** Otro formulario de la sección está abierto o guardando. */
  disabled: boolean
}

/** Mismos máximos que las columnas de V9. */
const LIMITS: Record<HeadingFieldName, number> = { eyebrow: 120, title: 200, body: 600 }
const LABELS: Record<HeadingFieldName, string> = {
  eyebrow: 'Antetítulo (opcional)',
  title: 'Título (opcional)',
  body: 'Texto (opcional)',
}
/** Secciones cuyo título no se ve en pantalla: lo anuncian los lectores de pantalla. */
const SCREEN_READER_ONLY: Partial<Record<HeadingSectionKey, string>> = {
  'page-header': 'Es el título principal de la página. Si lo dejas vacío se usa «Portafolio».',
  hero: 'Lo anuncian los lectores de pantalla como título principal. Si lo dejas vacío se usa el nombre del negocio.',
  'editorial-note': 'No se muestra en pantalla: lo anuncian los lectores de pantalla.',
  values: 'No se muestra en pantalla: lo anuncian los lectores de pantalla.',
}
const HEADING_TARGET = { id: 'heading' }

const toValues = (section: HeadingSection): HeadingValues => ({
  eyebrow: section.heading.eyebrow ?? '',
  title: section.heading.title ?? '',
  body: section.heading.body ?? '',
})

/**
 * Textos de cabecera de una sección (antes fijos en el código). Son contenido editorial: se guardan en el
 * borrador y se publican con la sección. Solo se ofrecen los campos que la sección admite.
 */
export function SectionHeadingEditor({ section, page, reload, notify, onFormOpenChange, disabled }: SectionHeadingEditorProps) {
  const { editing, busy, fieldErrors, setFieldErrors, run, startEdit, close, root } = useSectionEditor<typeof HEADING_TARGET>({
    sectionKey: `${section.sectionKey}:heading`, reload, notify, onFormOpenChange,
  })
  const fields = section.headingFields
  if (fields.length === 0) return null
  const current = toValues(section)
  const summary = fields.map((field) => current[field]).filter(Boolean)

  async function save(values: HeadingValues) {
    setFieldErrors({})
    const update = page
      ? () => adminApi.updateSectionHeading(section.sectionKey, values, section.revision, page)
      : () => adminApi.updateSectionHeading(section.sectionKey, values, section.revision)
    const ok = await run(update, 'Borrador guardado')
    if (ok) close()
  }

  return (
    <header ref={root} className="flex flex-col gap-2 border-b border-outline-variant pb-4">
      <p className="label-caps text-on-surface-variant">Textos de la sección</p>
      {editing ? (
        <HeadingForm sectionKey={section.sectionKey} fields={fields} initial={current} fieldErrors={fieldErrors}
          onSubmit={save} onCancel={close} />
      ) : (
        <>
          {summary.length > 0
            ? summary.map((text) => <p key={text} className="whitespace-pre-line break-words">{text}</p>)
            : <p className="text-on-surface-variant">Sin textos.</p>}
          <Button variant="text" className="self-start" data-edit-id={HEADING_TARGET.id} disabled={disabled || busy}
            onClick={() => startEdit(HEADING_TARGET)}>
            Editar textos de la sección
          </Button>
        </>
      )}
    </header>
  )
}

interface HeadingFormProps {
  sectionKey: HeadingSectionKey
  fields: HeadingFieldName[]
  initial: HeadingValues
  fieldErrors: Record<string, string>
  onSubmit: (values: HeadingValues) => Promise<void>
  onCancel: () => void
}

function HeadingForm({ sectionKey, fields, initial, fieldErrors, onSubmit, onCancel }: HeadingFormProps) {
  const [values, setValues] = useState(initial)
  const [saving, setSaving] = useState(false)
  const form = useRef<HTMLFormElement>(null)
  const hintId = useId()
  const bodyId = useId()
  const errorIds = { eyebrow: useId(), title: useId(), body: useId() }
  const hint = SCREEN_READER_ONLY[sectionKey]

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

  const set = (field: HeadingFieldName) => (value: string) => setValues((v) => ({ ...v, [field]: value }))
  const describedBy = (field: HeadingFieldName) =>
    [field === 'title' && hint ? hintId : null, fieldErrors[field] ? errorIds[field] : null].filter(Boolean).join(' ') || undefined

  return (
    <form ref={form} onSubmit={(e) => void handleSubmit(e)} className="flex flex-col gap-4 border border-outline-variant bg-surface-lowest p-4">
      {fields.map((field) => (
        <div key={field} className="flex flex-col gap-1.5">
          {field === 'body' ? (
            <>
              <label htmlFor={bodyId} className={labelClassName}>{LABELS.body}</label>
              <textarea id={bodyId} rows={3} maxLength={LIMITS.body} value={values.body}
                aria-invalid={fieldErrors.body ? true : undefined} aria-describedby={describedBy('body')}
                onChange={(e) => set('body')(e.target.value)} className={`${inputClassName} h-auto py-3`} />
            </>
          ) : (
            <TextField label={LABELS[field]} value={values[field]} maxLength={LIMITS[field]}
              invalid={!!fieldErrors[field]} describedBy={describedBy(field)} onChange={(e) => set(field)(e.target.value)} />
          )}
          {field === 'title' && hint && <p id={hintId} className="text-sm text-on-surface-variant">{hint}</p>}
          {fieldErrors[field] && <p id={errorIds[field]} className="text-sm text-error">{fieldErrors[field]}</p>}
        </div>
      ))}
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="text" onClick={onCancel}>Cancelar</Button>
        <Button type="submit" className="w-auto" disabled={saving}>Guardar borrador</Button>
      </div>
    </form>
  )
}
