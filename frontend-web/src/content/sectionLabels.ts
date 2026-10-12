import type { SectionKey } from '@/api/publicPage'

/** Nombres de las secciones en /admin/inicio y, sin título publicado, sus encabezados para lectores de pantalla. */
export const SECTION_LABELS: Record<SectionKey, string> = {
  hero: 'Hero',
  'editorial-note': 'Nota editorial',
  disciplines: 'Disciplinas',
  values: 'Valores',
  'featured-selection': 'Selección destacada',
  'closing-cta': 'Cierre',
}
