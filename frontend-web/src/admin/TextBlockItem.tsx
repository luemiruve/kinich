import type { AdminTextBlock } from '@/api/adminPage'
import { Button } from '@/components/Button'
import { TextBlock } from '@/components/editorial/TextBlock'

import { blockLabel } from './blockLabel'

const STATUS_LABEL = { DRAFT: 'Borrador', PUBLISHED: 'Publicado', HIDDEN: 'Oculto' } as const

interface TextBlockItemProps {
  block: AdminTextBlock
  canMoveUp: boolean
  canMoveDown: boolean
  onEdit: () => void
  onMove: (direction: -1 | 1) => void
  onToggleHidden: () => void
  onDelete: () => void
  /** Con un formulario abierto o una escritura en curso, las acciones de la sección se bloquean. */
  disabled?: boolean
}

export function TextBlockItem({ block, canMoveUp, canMoveDown, onEdit, onMove, onToggleHidden, onDelete, disabled }: TextBlockItemProps) {
  const label = blockLabel(block)
  return (
    <li className="flex flex-col gap-3 border-t border-outline-variant pt-4">
      <p className="label-caps text-on-surface-variant">
        {STATUS_LABEL[block.status]}
        {block.modified && ' · Cambios sin publicar'}
      </p>
      <div className={block.hidden ? 'opacity-60' : undefined}>
        <TextBlock block={block} />
      </div>
      <div className="flex flex-wrap gap-1">
        <Button variant="text" data-edit-id={block.id} disabled={disabled} aria-label={`Editar «${label}»`} onClick={onEdit}>Editar</Button>
        {canMoveUp && <Button variant="text" className="min-w-11" disabled={disabled} aria-label={`Subir «${label}»`} onClick={() => onMove(-1)}>↑</Button>}
        {canMoveDown && <Button variant="text" className="min-w-11" disabled={disabled} aria-label={`Bajar «${label}»`} onClick={() => onMove(1)}>↓</Button>}
        <Button variant="text" disabled={disabled} aria-label={`${block.hidden ? 'Mostrar' : 'Ocultar'} «${label}»`} onClick={onToggleHidden}>
          {block.hidden ? 'Mostrar' : 'Ocultar'}
        </Button>
        <Button variant="text" disabled={disabled} aria-label={`Eliminar «${label}»`} onClick={onDelete}>Eliminar</Button>
      </div>
    </li>
  )
}
