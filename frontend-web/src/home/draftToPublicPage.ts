import type { AdminPage } from '@/api/adminPage'
import type { PublicPage, SectionHeading } from '@/api/publicPage'

const isEmpty = (heading: SectionHeading) => !heading.eyebrow && !heading.title && !heading.body

/** Previsualización: el borrador con la forma pública (sin ocultos, en su orden). Mismos componentes que el sitio. */
export function draftToPublicPage(admin: AdminPage): PublicPage {
  return {
    pageKey: 'home',
    sections: admin.sections.map((section) => ({
      sectionKey: section.sectionKey,
      heading: isEmpty(section.heading) ? null : section.heading,
      texts: section.texts
        .filter((block) => !block.hidden)
        .sort((a, b) => a.position - b.position)
        .map(({ id, eyebrow, title, body }) => ({ id, eyebrow, title, body })),
      items: section.items
        .filter((item) => !item.hidden)
        .sort((a, b) => a.position - b.position)
        .map(({ id, image, category, eyebrow, title, description }) => ({
          id, image, category: category?.label ?? null, eyebrow, title, description,
        })),
    })),
  }
}
