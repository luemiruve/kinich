import type { ReactNode } from 'react'

import type { SectionHeading } from '@/api/publicPage'
import { SECTION_LABELS } from '@/content/sectionLabels'

import { Eyebrow } from './Eyebrow'

/**
 * Cierre editorial común: textos (eyebrow, título, cuerpo) y, opcionalmente, un medio. Sin botones ni iconos.
 * Con medio, dos columnas (texto + medio); sin él, bloque tonal centrado. Sin textos ni medio no se pinta.
 */
export function ClosingCta({ heading, media }: { heading: SectionHeading | null; media?: ReactNode }) {
  if (!heading && !media) return null
  return (
    <section data-section="closing-cta" {...(heading?.title ? { 'aria-labelledby': 'closing-cta-title' } : { 'aria-label': SECTION_LABELS['closing-cta'] })}
      className={media ? 'grid grid-cols-1 bg-surface-low md:grid-cols-2' : 'bg-surface-low'}>
      <div className={`flex min-w-0 flex-col justify-center gap-4 px-4 py-16 sm:px-6 md:px-16 ${media ? '' : 'items-center text-center'}`}>
        {heading?.eyebrow && <Eyebrow>{heading.eyebrow}</Eyebrow>}
        {heading?.title && (
          <h2 id="closing-cta-title" className="font-serif text-3xl break-words [overflow-wrap:anywhere] sm:text-4xl">{heading.title}</h2>
        )}
        {heading?.body && <p className="whitespace-pre-line break-words [overflow-wrap:anywhere] text-on-surface-variant">{heading.body}</p>}
      </div>
      {media}
    </section>
  )
}
