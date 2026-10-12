import type { PublicTextBlock, SectionHeading } from '@/api/publicPage'
import { TextBlock } from '@/components/editorial/TextBlock'
import { SECTION_LABELS } from '@/content/sectionLabels'

/** El encabezado es solo para lectores de pantalla: el título publicado o, sin él, el nombre de la sección. */
export function ValuesSection({ heading, blocks }: { heading: SectionHeading | null; blocks: PublicTextBlock[] }) {
  if (blocks.length === 0) return null
  return (
    <section data-section="values" aria-labelledby="values-title" className="bg-surface-low px-4 py-16 sm:px-6 md:px-16 md:py-24">
      <h2 id="values-title" className="sr-only">{heading?.title ?? SECTION_LABELS.values}</h2>
      <ul className="grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-3">
        {blocks.map((block) => (
          <li key={block.id} className="min-w-0 border-t border-outline-variant pt-6">
            <TextBlock block={block} />
          </li>
        ))}
      </ul>
    </section>
  )
}
