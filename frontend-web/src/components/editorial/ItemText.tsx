import type { PublicItem } from '@/api/publicPage'

import { Eyebrow } from './Eyebrow'

/**
 * Textos de una tarjeta (Disciplinas, Selección destacada y Portafolio). Los opcionales vacíos no se pintan;
 * `titleId` permite que un enlace superpuesto tome el título como nombre accesible.
 */
export function ItemText({ item, titleId }: { item: PublicItem; titleId?: string }) {
  return (
    <>
      {item.category && <p className="label-caps text-on-surface-variant">{item.category}</p>}
      {item.eyebrow && <Eyebrow>{item.eyebrow}</Eyebrow>}
      {item.title && <h3 id={titleId} className="font-serif text-2xl break-words [overflow-wrap:anywhere]">{item.title}</h3>}
      {item.description && (
        <p className="whitespace-pre-line break-words [overflow-wrap:anywhere] text-on-surface-variant">{item.description}</p>
      )}
    </>
  )
}
