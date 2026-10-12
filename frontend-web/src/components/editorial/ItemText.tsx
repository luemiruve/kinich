import type { PublicItem } from '@/api/publicPage'

import { Eyebrow } from './Eyebrow'

/** Textos de una tarjeta (Disciplinas y Selección destacada). Los opcionales vacíos no se pintan. */
export function ItemText({ item }: { item: PublicItem }) {
  return (
    <>
      {item.category && <p className="label-caps text-on-surface-variant">{item.category}</p>}
      {item.eyebrow && <Eyebrow>{item.eyebrow}</Eyebrow>}
      {item.title && <h3 className="font-serif text-2xl break-words [overflow-wrap:anywhere]">{item.title}</h3>}
      {item.description && (
        <p className="whitespace-pre-line break-words [overflow-wrap:anywhere] text-on-surface-variant">{item.description}</p>
      )}
    </>
  )
}
