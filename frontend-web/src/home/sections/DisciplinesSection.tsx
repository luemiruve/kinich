// Hasta 4 tarjetas; clases estáticas por cantidad
import type { PublicItem, SectionHeading } from '@/api/publicPage'
import { Eyebrow } from '@/components/editorial/Eyebrow'
import { ItemText } from '@/components/editorial/ItemText'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { disciplinesSizes } from '@/content/imageSizes'
import { SECTION_LABELS } from '@/content/sectionLabels'

const COLUMNS: Record<number, string> = {
  1: 'grid-cols-1',
  2: 'grid-cols-1 sm:grid-cols-2',
  3: 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3',
  4: 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-4',
}

/** Sin título publicado, la sección conserva un encabezado solo para lectores de pantalla. */
export function DisciplinesSection({ heading, items }: { heading: SectionHeading | null; items: PublicItem[] }) {
  if (items.length === 0) return null
  const visible = items.slice(0, 4)
  return (
    <section data-section="disciplines" aria-labelledby="disciplines-title" className="px-4 py-16 sm:px-6 md:px-16 md:py-24">
      <div className="flex flex-col gap-3">
        {heading?.eyebrow && <Eyebrow>{heading.eyebrow}</Eyebrow>}
        <h2 id="disciplines-title" className={heading?.title ? 'font-serif text-3xl break-words [overflow-wrap:anywhere] sm:text-4xl' : 'sr-only'}>
          {heading?.title ?? SECTION_LABELS.disciplines}
        </h2>
      </div>
      <ul className={`mt-10 grid gap-8 ${COLUMNS[visible.length] ?? COLUMNS[4]}`}>
        {visible.map((item) => (
          <li key={item.id} className="flex min-w-0 flex-col gap-4">
            <MediaFrame image={item.image} sizes={disciplinesSizes(visible.length)} className="aspect-[4/5]" />
            <ItemText item={item} />
          </li>
        ))}
      </ul>
    </section>
  )
}
