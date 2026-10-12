import type { AdminItem } from '@/api/adminPage'

/** Nombre accesible de un elemento en los botones de acción y en la confirmación de borrado. */
export function itemLabel(item: AdminItem): string {
  return item.title ?? item.eyebrow ?? item.image.alt
}
