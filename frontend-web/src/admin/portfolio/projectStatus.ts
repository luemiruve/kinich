import type { AdminProjectSummary } from '@/api/adminPortfolio'

type StatusFields = Pick<AdminProjectSummary, 'status' | 'modified' | 'hidden'>

/**
 * Estado visible. Oculto con versión publicada = «Retirada pendiente»: retirar sigue el flujo de publicación (D-16)
 * y el proyecto sigue en el sitio hasta que se publica.
 */
export function statusLabel({ status, modified, hidden }: StatusFields): string {
  if (status === 'PUBLISHED') {
    if (hidden) return 'Retirada pendiente'
    return modified ? 'Publicado con cambios' : 'Publicado'
  }
  return status === 'HIDDEN' ? 'Oculto' : 'Borrador'
}

/** Publicar solo tiene efecto con algo pendiente: un borrador nuevo o cambios (incluida una retirada). */
export function canPublish({ status, modified }: StatusFields): boolean {
  return status === 'DRAFT' || (status === 'PUBLISHED' && modified)
}

export const PENDING_WITHDRAWAL_HINT = 'Sigue visible en el sitio hasta que publiques el proyecto.'
