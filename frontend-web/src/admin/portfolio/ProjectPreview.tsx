import { useEffect, useState, type MouseEvent } from 'react'

import { fetchCategories, type Category } from '@/api/adminMedia'
import type { PublicProject, PublicProjectCard } from '@/api/publicPortfolio'
import { PortfolioProjectContent } from '@/portfolio/PortfolioProjectPage'
import { ProjectCard } from '@/portfolio/ProjectCard'

import { cropFromControls } from '../framing'
import type { ProjectDraft } from './useProjectEditor'

/** Los enlaces de la vista previa no navegan: sacarían del panel con el borrador sin guardar. */
function blockLinks(event: MouseEvent) {
  if ((event.target as HTMLElement).closest('a')) event.preventDefault()
}

/** Vista previa con los componentes públicos (tarjeta del listado y detalle) y los datos del borrador. */
export function ProjectPreview({ draft, categoryLabel }: { draft: ProjectDraft; categoryLabel: string | null }) {
  const [categories, setCategories] = useState<Category[]>([])

  useEffect(() => {
    const controller = new AbortController()
    fetchCategories(controller.signal).then(setCategories).catch(() => setCategories([]))
    return () => controller.abort()
  }, [])

  const category = categories.find((c) => c.id === draft.categoryId)?.label ?? (draft.categoryId ? categoryLabel : null)
  const { cover } = draft
  const card: PublicProjectCard | null = cover.image && {
    slug: draft.slug,
    title: draft.title,
    eyebrow: draft.eyebrow || null,
    category,
    description: draft.description || null,
    // Sin encuadre guardado, el servidor centra el 4:5: se reproduce aquí.
    cover: {
      ...cover.image, alt: cover.alt, mediaPosition: null,
      crop: cover.crop ?? cropFromControls(cover.image.width, cover.image.height, { zoom: 1, x: 0.5, y: 0.5 }),
    },
  }
  const detail: PublicProject = {
    slug: draft.slug,
    title: draft.title,
    eyebrow: draft.eyebrow || null,
    category,
    description: draft.description || null,
    images: draft.images.map((entry) => ({
      image: { ...entry.image, alt: entry.alt, crop: null, mediaPosition: null },
      caption: entry.caption || null,
    })),
  }

  return (
    <div onClickCapture={blockLinks} className="flex flex-col gap-12">
      <section aria-label="Tarjeta en el listado" className="px-4 sm:px-6 md:px-16">
        <p className="label-caps mb-4 text-on-surface-variant">Tarjeta en el listado</p>
        {card
          ? <div className="max-w-sm"><ProjectCard project={card} /></div>
          : <p>Elige una portada para ver la tarjeta.</p>}
      </section>
      <section aria-label="Página del proyecto" className="border-t border-outline-variant">
        <PortfolioProjectContent project={detail} />
      </section>
    </div>
  )
}
