// Variante de Inicio: texto a la izquierda, imagen a la derecha; sin botones de acción
import type { PublicItem, SectionHeading } from '@/api/publicPage'
import { Eyebrow } from '@/components/editorial/Eyebrow'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { IMAGE_SIZES } from '@/content/imageSizes'
import { SECTION_LABELS } from '@/content/sectionLabels'

/** Textos (contenido editorial publicado) y foto publicada. Sin ninguno de los dos, la sección no se muestra. */
export function ClosingCtaSection({ heading, item }: { heading: SectionHeading | null; item?: PublicItem }) {
  if (!heading && !item) return null
  return (
    <section data-section="closing-cta" {...(heading?.title ? { 'aria-labelledby': 'closing-cta-title' } : { 'aria-label': SECTION_LABELS['closing-cta'] })}
      className="grid grid-cols-1 bg-surface-low md:grid-cols-2">
      <div className="flex min-w-0 flex-col justify-center gap-4 px-4 py-16 sm:px-6 md:px-16">
        {heading?.eyebrow && <Eyebrow>{heading.eyebrow}</Eyebrow>}
        {heading?.title && (
          <h2 id="closing-cta-title" className="font-serif text-3xl break-words [overflow-wrap:anywhere] sm:text-4xl">{heading.title}</h2>
        )}
        {heading?.body && <p className="whitespace-pre-line break-words [overflow-wrap:anywhere] text-on-surface-variant">{heading.body}</p>}
      </div>
      <MediaFrame image={item?.image} sizes={IMAGE_SIZES.halfFromMd} className="min-h-72" />
    </section>
  )
}
