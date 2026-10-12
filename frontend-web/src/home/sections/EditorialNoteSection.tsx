import type { PublicTextBlock, SectionHeading } from '@/api/publicPage'
import { TextBlock } from '@/components/editorial/TextBlock'
import { SECTION_LABELS } from '@/content/sectionLabels'

/** El encabezado es solo para lectores de pantalla: el título publicado o, sin él, el nombre de la sección. */
export function EditorialNoteSection({ heading, block }: { heading: SectionHeading | null; block?: PublicTextBlock }) {
  if (!block) return null
  return (
    <section data-section="editorial-note" aria-labelledby="editorial-note-title" className="px-4 py-16 sm:px-6 md:px-16 md:py-24">
      <h2 id="editorial-note-title" className="sr-only">{heading?.title ?? SECTION_LABELS['editorial-note']}</h2>
      <div className="mx-auto max-w-3xl">
        <TextBlock block={block} headingLevel={3} />
      </div>
    </section>
  )
}
