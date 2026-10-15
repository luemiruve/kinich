import { useId } from 'react'

import { TextField } from '@/components/form/TextField'
import { inputClassName, labelClassName } from '@/components/form/fieldStyles'
import { PORTFOLIO_LIMITS } from '@/content/textLimits'

import { CategorySelect } from '../CategorySelect'
import type { ProjectDraft } from './useProjectEditor'

interface ProjectFormProps {
  draft: ProjectDraft
  /** Slug de la versión publicada: si difiere, la dirección pública cambia al publicar. */
  publishedSlug: string | null
  fieldErrors: Record<string, string>
  onTitle: (title: string) => void
  onSlug: (slug: string) => void
  onChange: (patch: Partial<ProjectDraft>) => void
}

const describe = (...ids: (string | false | undefined)[]) => ids.filter(Boolean).join(' ') || undefined

/** Datos del proyecto: título, slug (sugerido a partir del título), antetítulo, categoría y descripción. */
export function ProjectForm({ draft, publishedSlug, fieldErrors, onTitle, onSlug, onChange }: ProjectFormProps) {
  const ids = { title: useId(), slug: useId(), slugHint: useId(), eyebrow: useId(), description: useId(), descriptionField: useId(), counter: useId() }
  const error = (field: string, id: string) => fieldErrors[field] && <p id={id} className="text-sm text-error">{fieldErrors[field]}</p>
  return (
    <fieldset className="flex flex-col gap-4">
      <legend className="font-serif text-2xl">Datos del proyecto</legend>
      <div className="flex flex-col gap-1.5">
        <TextField label="Título" value={draft.title} required maxLength={PORTFOLIO_LIMITS.title}
          invalid={!!fieldErrors.title} describedBy={describe(fieldErrors.title && ids.title)}
          onChange={(e) => onTitle(e.target.value)} />
        {error('title', ids.title)}
      </div>
      <div className="flex flex-col gap-1.5">
        <TextField label="Slug (dirección web)" value={draft.slug} required maxLength={PORTFOLIO_LIMITS.slug}
          invalid={!!fieldErrors.slug} describedBy={describe(ids.slugHint, fieldErrors.slug && ids.slug)}
          onChange={(e) => onSlug(e.target.value)} />
        <p id={ids.slugHint} className="text-sm text-on-surface-variant break-all">
          /portafolio/{draft.slug || '…'} · minúsculas, números y guiones.
          {publishedSlug && publishedSlug !== draft.slug && ` Publicado como /portafolio/${publishedSlug} hasta que publiques.`}
        </p>
        {error('slug', ids.slug)}
      </div>
      <div className="flex flex-col gap-1.5">
        <TextField label="Antetítulo (opcional)" value={draft.eyebrow} maxLength={PORTFOLIO_LIMITS.eyebrow}
          invalid={!!fieldErrors.eyebrow} describedBy={describe(fieldErrors.eyebrow && ids.eyebrow)}
          onChange={(e) => onChange({ eyebrow: e.target.value })} />
        {error('eyebrow', ids.eyebrow)}
      </div>
      <CategorySelect value={draft.categoryId} onChange={(categoryId) => onChange({ categoryId })} error={fieldErrors.categoryId} />
      <div className="flex flex-col gap-1.5">
        <label htmlFor={ids.descriptionField} className={labelClassName}>Descripción (opcional)</label>
        <textarea id={ids.descriptionField} rows={5} maxLength={PORTFOLIO_LIMITS.description} value={draft.description}
          aria-invalid={fieldErrors.description ? true : undefined}
          aria-describedby={describe(ids.counter, fieldErrors.description && ids.description)}
          onChange={(e) => onChange({ description: e.target.value })} className={`${inputClassName} h-auto py-3`} />
        {/* String.length cuenta unidades UTF-16, igual que @Size en el backend. */}
        <p id={ids.counter} className="text-right text-sm text-on-surface-variant">
          {draft.description.length} / {PORTFOLIO_LIMITS.description}
        </p>
        {error('description', ids.description)}
      </div>
    </fieldset>
  )
}
