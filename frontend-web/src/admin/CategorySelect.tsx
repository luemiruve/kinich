import { useEffect, useId, useState } from 'react'

import { createCategory, fetchCategories, type Category } from '@/api/adminMedia'
import { ApiError } from '@/api/http'
import { Button } from '@/components/Button'
import { inputClassName, labelClassName } from '@/components/form/fieldStyles'
import { MEDIA_LIMITS } from '@/content/mediaLimits'

interface CategorySelectProps {
  value: string | null
  onChange: (id: string | null) => void
  error?: string
}

/** Elegir una categoría existente o crear una nueva (solo listar y crear). */
export function CategorySelect({ value, onChange, error }: CategorySelectProps) {
  const selectId = useId()
  const newId = useId()
  const errorId = useId()
  const [categories, setCategories] = useState<Category[]>([])
  const [label, setLabel] = useState('')
  const [createError, setCreateError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    fetchCategories(controller.signal).then(setCategories).catch(() => setCategories([]))
    return () => controller.abort()
  }, [])

  async function create() {
    setCreateError('')
    try {
      const created = await createCategory(label)
      setCategories((current) => [...current, created].sort((a, b) => a.label.localeCompare(b.label, 'es')))
      setLabel('')
      onChange(created.id)
    } catch (failure) {
      setCreateError(failure instanceof ApiError ? (failure.fieldErrors.label ?? failure.message) : 'No se pudo crear.')
    }
  }

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={selectId} className={labelClassName}>Categoría (opcional)</label>
      <select id={selectId} value={value ?? ''} onChange={(e) => onChange(e.target.value || null)}
        aria-invalid={error ? true : undefined} aria-describedby={error ? errorId : undefined} className={inputClassName}>
        <option value="">Sin categoría</option>
        {categories.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
      </select>
      {error && <p id={errorId} className="text-sm text-error">{error}</p>}
      <div className="flex flex-wrap items-end gap-2">
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <label htmlFor={newId} className={labelClassName}>Nueva categoría</label>
          <input id={newId} value={label} maxLength={MEDIA_LIMITS.categoryLabel} onChange={(e) => setLabel(e.target.value)}
            className={inputClassName} />
        </div>
        <Button variant="text" disabled={label.trim() === ''} onClick={() => void create()}>Crear categoría</Button>
      </div>
      {createError && <p role="alert" className="text-sm text-error">{createError}</p>}
    </div>
  )
}
