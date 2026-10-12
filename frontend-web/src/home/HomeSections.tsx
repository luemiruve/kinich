import type { PublicPage, PublicSection } from '@/api/publicPage'

import { ClosingCtaSection } from './sections/ClosingCtaSection'
import { DisciplinesSection } from './sections/DisciplinesSection'
import { EditorialNoteSection } from './sections/EditorialNoteSection'
import { FeaturedSection } from './sections/FeaturedSection'
import { HeroSection } from './sections/HeroSection'
import { ValuesSection } from './sections/ValuesSection'

/**
 * Compone Inicio en el orden del catálogo. El cierre va siempre al final.
 * Se usa en la página pública y en la previsualización del borrador. Los textos de cabecera son contenido
 * publicado; siteName (nombre de la instalación) es el título del Hero si no tiene uno propio.
 */
export function HomeSections({ page, siteName }: { page: PublicPage; siteName: string }) {
  const ordered = [
    ...page.sections.filter((s) => s.sectionKey !== 'closing-cta'),
    ...page.sections.filter((s) => s.sectionKey === 'closing-cta'),
  ]
  return <>{ordered.map((section) => renderSection(section, siteName))}</>
}

function renderSection(section: PublicSection, siteName: string) {
  const heading = section.heading
  switch (section.sectionKey) {
    case 'hero':
      return <HeroSection key="hero" title={heading?.title ?? siteName} item={section.items[0]} />
    case 'editorial-note':
      return <EditorialNoteSection key="editorial-note" heading={heading} block={section.texts[0]} />
    case 'disciplines':
      return <DisciplinesSection key="disciplines" heading={heading} items={section.items} />
    case 'values':
      return <ValuesSection key="values" heading={heading} blocks={section.texts} />
    case 'featured-selection':
      return <FeaturedSection key="featured-selection" heading={heading} items={section.items} />
    case 'closing-cta':
      return <ClosingCtaSection key="closing-cta" heading={heading} item={section.items[0]} />
  }
}
