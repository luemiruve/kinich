import type { AdminTextBlock } from '@/api/adminPage'

/** Nombre accesible de un bloque en los botones de acción y en la confirmación de borrado. */
export function blockLabel(block: AdminTextBlock): string {
  return block.title ?? block.eyebrow ?? block.body.slice(0, 40)
}
