import { useId } from 'react'
import { Link } from 'react-router'

import type { AdminProjectSummary } from '@/api/adminPortfolio'
import { Button } from '@/components/Button'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { IMAGE_SIZES } from '@/content/imageSizes'

import { canPublish, PENDING_WITHDRAWAL_HINT, statusLabel } from './projectStatus'

export type MoveDirection = 'up' | 'down'

interface ProjectListItemProps {
  project: AdminProjectSummary
  canMoveUp: boolean
  canMoveDown: boolean
  /** Con una escritura en curso las acciones se bloquean. */
  disabled: boolean
  onMove: (direction: MoveDirection) => void
  onPublish: () => void
  onToggleHidden: () => void
  onDelete: () => void
}

const linkClassName =
  'label-caps inline-flex min-h-11 items-center px-2 underline decoration-secondary underline-offset-4 hover:text-secondary'

/** Fila de la lista: portada, título, estado y acciones. Subir/Bajar llevan `data-move` para recuperar el foco. */
export function ProjectListItem({ project, canMoveUp, canMoveDown, disabled, onMove, onPublish, onToggleHidden, onDelete }: ProjectListItemProps) {
  const titleId = useId()
  const { id, title } = project
  const pendingWithdrawal = project.status === 'PUBLISHED' && project.hidden
  return (
    <li aria-labelledby={titleId} className="flex flex-col gap-3 border-t border-outline-variant pt-4">
      <div className={`flex flex-wrap gap-4 ${project.hidden ? 'opacity-60' : ''}`}>
        <MediaFrame image={project.cover} sizes={IMAGE_SIZES.thumbnail} className="aspect-[4/5] w-24 shrink-0" />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <p className="label-caps text-on-surface-variant">{statusLabel(project)}</p>
          <h3 id={titleId} className="font-serif text-xl break-words [overflow-wrap:anywhere]">{title}</h3>
          <p className="text-sm text-on-surface-variant break-all">/portafolio/{project.slug}</p>
          {project.category && <p className="text-sm text-on-surface-variant">{project.category.label}</p>}
          {pendingWithdrawal && <p className="text-sm">{PENDING_WITHDRAWAL_HINT}</p>}
        </div>
      </div>
      <div className="flex flex-wrap gap-1">
        <Link to={`/admin/portafolio/${id}`} aria-label={`Editar «${title}»`} className={linkClassName}>Editar</Link>
        {canMoveUp && (
          <Button variant="text" className="min-w-11" data-move={`${id}:up`} disabled={disabled}
            aria-label={`Subir «${title}»`} onClick={() => onMove('up')}>↑</Button>
        )}
        {canMoveDown && (
          <Button variant="text" className="min-w-11" data-move={`${id}:down`} disabled={disabled}
            aria-label={`Bajar «${title}»`} onClick={() => onMove('down')}>↓</Button>
        )}
        <Button variant="text" disabled={disabled || !canPublish(project)} aria-label={`Publicar «${title}»`} onClick={onPublish}>
          Publicar
        </Button>
        <Button variant="text" disabled={disabled} aria-label={`${project.hidden ? 'Mostrar' : 'Retirar'} «${title}»`}
          onClick={onToggleHidden}>
          {project.hidden ? 'Mostrar' : 'Retirar'}
        </Button>
        <Button variant="text" disabled={disabled} aria-label={`Eliminar «${title}»`} onClick={onDelete}>Eliminar</Button>
      </div>
    </li>
  )
}
