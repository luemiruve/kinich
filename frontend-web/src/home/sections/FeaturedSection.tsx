// Carrusel manual (sin autoplay), scroll-snap, controles de 44 px
import { useRef } from 'react'

import type { PublicItem, SectionHeading } from '@/api/publicPage'
import { Eyebrow } from '@/components/editorial/Eyebrow'
import { ItemText } from '@/components/editorial/ItemText'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { IMAGE_SIZES } from '@/content/imageSizes'
import { SECTION_LABELS } from '@/content/sectionLabels'

const controlClass =
  'flex h-11 w-11 cursor-pointer items-center justify-center border border-outline text-on-surface hover:border-on-surface'

export function FeaturedSection({ heading, items }: { heading: SectionHeading | null; items: PublicItem[] }) {
  const track = useRef<HTMLDivElement>(null)
  const title = heading?.title ?? SECTION_LABELS['featured-selection']

  function move(direction: 1 | -1) {
    const element = track.current
    if (!element) return
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    element.scrollBy?.({ left: direction * element.clientWidth, behavior: reduced ? 'auto' : 'smooth' })
  }

  if (items.length === 0) return null

  return (
    <section
      data-section="featured-selection"
      aria-roledescription="carrusel"
      aria-label={title}
      className="px-4 py-16 sm:px-6 md:px-16 md:py-24"
    >
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-3">
          {heading?.eyebrow && <Eyebrow>{heading.eyebrow}</Eyebrow>}
          <h2 className={heading?.title ? 'font-serif text-3xl break-words sm:text-4xl' : 'sr-only'}>{title}</h2>
        </div>
        <div className="flex gap-2">
          <button type="button" className={controlClass} aria-label="Anterior" onClick={() => move(-1)}>←</button>
          <button type="button" className={controlClass} aria-label="Siguiente" onClick={() => move(1)}>→</button>
        </div>
      </div>
      <div
        ref={track}
        tabIndex={0}
        aria-label={`${title}: desplázate para ver más`}
        className="mt-10 flex snap-x snap-mandatory gap-6 overflow-x-auto pb-4"
      >
        {items.map((item, index) => (
          <div
            key={item.id}
            role="group"
            aria-roledescription="diapositiva"
            aria-label={`${index + 1} de ${items.length}`}
            className="w-[85%] shrink-0 snap-start sm:w-[45%] lg:w-[30%]"
          >
            <MediaFrame image={item.image} sizes={IMAGE_SIZES.featured} className="aspect-[4/5]" />
            <div className="mt-4 flex flex-col gap-2">
              <ItemText item={item} />
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}
