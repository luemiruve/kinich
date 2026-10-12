import { useEffect, useId, useRef, useState, type FormEvent } from 'react'

import {
  createSocialLink, deleteSocialLink, reorderSocialLinks, updateSocialLink,
  type AdminSettings, type AdminSocialLink, type SocialLinkValues,
} from '@/api/adminSettings'
import type { SocialPlatform } from '@/api/site'
import { Button } from '@/components/Button'
import { TextField } from '@/components/form/TextField'
import { inputClassName, labelClassName } from '@/components/form/fieldStyles'
import { PLATFORM_LABELS, SETTINGS_LIMITS, SOCIAL_PLATFORMS, socialLabel } from '@/content/site'

import { ConfirmDeleteDialog } from '../ConfirmDeleteDialog'
import { FieldError } from './FieldError'
import type { useSettings } from './useSettings'

type Run = ReturnType<typeof useSettings>['run']
type Editing = { mode: 'create' } | { mode: 'edit'; link: AdminSocialLink } | null

const EMPTY: SocialLinkValues = { platform: 'INSTAGRAM', label: '', url: 'https://', visible: true }

/** Colección ordenable de redes sociales. Cada cambio se aplica al guardar (sin publicación). */
export function SocialLinksEditor({ settings, run }: { settings: AdminSettings; run: Run }) {
  const links = [...settings.socialLinks].sort((a, b) => a.position - b.position)
  const [editing, setEditing] = useState<Editing>(null)
  const [deleting, setDeleting] = useState<AdminSocialLink | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const full = links.length >= SETTINGS_LIMITS.maxLinks

  async function act(action: () => Promise<unknown>, success: string, onDone?: () => void) {
    setBusy(true)
    const ok = await run(action, success, setErrors)
    setBusy(false)
    if (ok) onDone?.()
  }

  function close(focus: string) {
    setEditing(null)
    // Foco de vuelta al disparador (WCAG 2.4.3).
    requestAnimationFrame(() => root.current?.querySelector<HTMLElement>(focus)?.focus())
  }

  function save(values: SocialLinkValues) {
    if (editing?.mode === 'edit') {
      const link = editing.link
      return act(() => updateSocialLink(link.id, values, link.version), 'Red social guardada', () => close(`[data-edit-id="${link.id}"]`))
    }
    return act(() => createSocialLink(values), 'Red social añadida', () => close('[data-new-link]'))
  }

  function move(index: number, direction: -1 | 1) {
    const order = links.map(({ id, version }) => ({ id, version }))
    ;[order[index], order[index + direction]] = [order[index + direction], order[index]]
    void act(() => reorderSocialLinks(order), 'Orden guardado')
  }

  const blocked = busy || editing !== null
  return (
    <div ref={root} className="flex flex-col gap-4">
      {links.length === 0 && <p className="text-on-surface-variant">Todavía no hay redes sociales.</p>}
      <ul className="flex flex-col gap-3">
        {links.map((link, index) => (
          <li key={link.id} className="flex flex-col gap-2 border border-outline-variant p-4">
            {editing?.mode === 'edit' && editing.link.id === link.id ? (
              <SocialLinkForm initial={{ platform: link.platform, label: link.label ?? '', url: link.url, visible: link.visible }}
                errors={errors} onSubmit={save} onCancel={() => close(`[data-edit-id="${link.id}"]`)} />
            ) : (
              <>
                <p className="font-serif text-lg break-words">
                  {socialLabel(link)}
                  {!link.visible && <span className="ml-2 label-caps text-secondary">Oculta</span>}
                </p>
                <p className="text-sm break-all text-on-surface-variant">{link.url}</p>
                <div className="flex flex-wrap gap-1">
                  <Button variant="text" data-edit-id={link.id} disabled={blocked}
                    onClick={() => { setErrors({}); setEditing({ mode: 'edit', link }) }}>Editar</Button>
                  <Button variant="text" disabled={blocked || index === 0} aria-label={`Subir ${socialLabel(link)}`}
                    onClick={() => move(index, -1)}>Subir</Button>
                  <Button variant="text" disabled={blocked || index === links.length - 1} aria-label={`Bajar ${socialLabel(link)}`}
                    onClick={() => move(index, 1)}>Bajar</Button>
                  <Button variant="text" disabled={blocked} onClick={() => setDeleting(link)}>Eliminar</Button>
                </div>
              </>
            )}
          </li>
        ))}
      </ul>
      {editing?.mode === 'create' ? (
        <SocialLinkForm initial={EMPTY} errors={errors} onSubmit={save} onCancel={() => close('[data-new-link]')} />
      ) : (
        <Button variant="text" className="self-start" data-new-link disabled={blocked || full}
          onClick={() => { setErrors({}); setEditing({ mode: 'create' }) }}>
          Nueva red social
        </Button>
      )}
      {full && <p className="text-sm text-on-surface-variant">Has alcanzado el máximo de {SETTINGS_LIMITS.maxLinks} redes sociales.</p>}
      {deleting && (
        <ConfirmDeleteDialog
          label={socialLabel(deleting)}
          description="Se quita del sitio en cuanto confirmes."
          onCancel={() => setDeleting(null)}
          onConfirm={() => {
            const target = deleting
            setDeleting(null)
            void act(() => deleteSocialLink(target.id, target.version), 'Red social eliminada')
          }}
        />
      )}
    </div>
  )
}

interface SocialLinkFormProps {
  initial: SocialLinkValues
  errors: Record<string, string>
  onSubmit: (values: SocialLinkValues) => Promise<void>
  onCancel: () => void
}

function SocialLinkForm({ initial, errors, onSubmit, onCancel }: SocialLinkFormProps) {
  const [values, setValues] = useState(initial)
  const [saving, setSaving] = useState(false)
  const form = useRef<HTMLFormElement>(null)
  const ids = { platform: useId(), platformError: useId(), label: useId(), url: useId(), urlHint: useId(), visible: useId() }
  const other = values.platform === 'OTHER'

  useEffect(() => {
    form.current?.querySelector<HTMLElement>('select')?.focus()
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    try {
      await onSubmit(values)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form ref={form} onSubmit={(e) => void submit(e)} className="flex flex-col gap-4 border border-outline-variant bg-surface-lowest p-4">
      <div className="flex flex-col gap-1.5">
        <label htmlFor={ids.platform} className={labelClassName}>Plataforma</label>
        <select id={ids.platform} value={values.platform} className={inputClassName}
          aria-invalid={errors.platform ? true : undefined} aria-describedby={errors.platform ? ids.platformError : undefined}
          onChange={(e) => setValues((v) => ({ ...v, platform: e.target.value as SocialPlatform }))}>
          {SOCIAL_PLATFORMS.map((platform) => <option key={platform} value={platform}>{PLATFORM_LABELS[platform]}</option>)}
        </select>
        <FieldError id={ids.platformError} message={errors.platform} />
      </div>
      <TextField label={other ? 'Nombre de la plataforma' : 'Nombre visible (opcional)'} value={values.label} required={other}
        maxLength={SETTINGS_LIMITS.linkLabel} invalid={!!errors.label} describedBy={errors.label ? ids.label : undefined}
        onChange={(e) => setValues((v) => ({ ...v, label: e.target.value }))} />
      <FieldError id={ids.label} message={errors.label} />
      <TextField label="Enlace" type="url" inputMode="url" value={values.url} required maxLength={SETTINGS_LIMITS.url}
        invalid={!!errors.url} describedBy={[ids.urlHint, errors.url ? ids.url : null].filter(Boolean).join(' ')}
        onChange={(e) => setValues((v) => ({ ...v, url: e.target.value }))} />
      <p id={ids.urlHint} className="text-sm text-on-surface-variant">Solo enlaces seguros que empiecen por https://</p>
      <FieldError id={ids.url} message={errors.url} />
      <div className="flex min-h-11 items-center gap-3">
        <input id={ids.visible} type="checkbox" checked={values.visible} className="h-5 w-5 accent-secondary"
          onChange={(e) => setValues((v) => ({ ...v, visible: e.target.checked }))} />
        <label htmlFor={ids.visible}>Mostrar en el sitio</label>
      </div>
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="text" onClick={onCancel}>Cancelar</Button>
        <Button type="submit" className="w-auto" disabled={saving}>Guardar</Button>
      </div>
    </form>
  )
}
