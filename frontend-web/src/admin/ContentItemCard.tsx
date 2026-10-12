import type { AdminItem } from '@/api/adminPage'
import { Button } from '@/components/Button'
import { ItemText } from '@/components/editorial/ItemText'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { IMAGE_SIZES } from '@/content/imageSizes'

import { itemLabel } from './itemLabel'

const STATUS_LABEL = { DRAFT: 'Borrador', PUBLISHED: 'Publicado', HIDDEN: 'Oculto' } as const

interface ContentItemCardProps {
  item: AdminItem
  canMoveUp: boolean
  canMoveDown: boolean
  onEdit: () => void
  onMove: (direction: -1 | 1) => void
  onToggleHidden: () => void
  onDelete: () => void
  /** Con un formulario abierto o una escritura en curso, las acciones de la sección se bloquean. */
  disabled?: boolean
}

export function ContentItemCard({ item, canMoveUp, canMoveDown, onEdit, onMove, onToggleHidden, onDelete, disabled }: ContentItemCardProps) {
  const label = itemLabel(item)
  return (
    <li className="flex flex-col gap-3 border-t border-outline-variant pt-4">
      <p className="label-caps text-on-surface-variant">
        {STATUS_LABEL[item.status]}
        {item.modified && ' · Cambios sin publicar'}
      </p>
      <div className={`flex flex-wrap gap-4 ${item.hidden ? 'opacity-60' : ''}`}>
        {/* Miniatura con el encuadre del elemento: 4:5 si es un rectángulo; cuadrada con su punto focal si no. */}
        <MediaFrame image={item.image} sizes={IMAGE_SIZES.thumbnail}
          className={`w-32 shrink-0 ${item.image.crop ? 'aspect-[4/5]' : 'aspect-square'}`} />
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <ItemText item={{ ...item, category: item.category?.label ?? null }} />
        </div>
      </div>
      <div className="flex flex-wrap gap-1">
        <Button variant="text" data-edit-id={item.id} disabled={disabled} aria-label={`Editar «${label}»`} onClick={onEdit}>Editar</Button>
        {canMoveUp && <Button variant="text" className="min-w-11" disabled={disabled} aria-label={`Subir «${label}»`} onClick={() => onMove(-1)}>↑</Button>}
        {canMoveDown && <Button variant="text" className="min-w-11" disabled={disabled} aria-label={`Bajar «${label}»`} onClick={() => onMove(1)}>↓</Button>}
        <Button variant="text" disabled={disabled} aria-label={`${item.hidden ? 'Mostrar' : 'Ocultar'} «${label}»`} onClick={onToggleHidden}>
          {item.hidden ? 'Mostrar' : 'Ocultar'}
        </Button>
        <Button variant="text" disabled={disabled} aria-label={`Eliminar «${label}»`} onClick={onDelete}>Eliminar</Button>
      </div>
    </li>
  )
}
