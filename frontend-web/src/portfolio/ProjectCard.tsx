import { useId } from 'react'
import { Link } from 'react-router'

import type { PublicProjectCard } from '@/api/publicPortfolio'
import { ItemText } from '@/components/editorial/ItemText'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { PORTFOLIO_CARD_SIZES } from '@/content/imageSizes'

/**
 * Tarjeta con enlace superpuesto: el contenido (portada con su alt y textos) queda fuera del enlace, que cubre toda
 * la tarjeta y toma su nombre accesible del título. Hover y foco se ven en la tarjeta.
 */
export function ProjectCard({ project }: { project: PublicProjectCard }) {
  const titleId = useId()
  return (
    <div className="relative flex h-full flex-col gap-4 bg-surface-container-lowest p-4 transition-colors duration-200 hover:bg-surface-low has-[a:focus-visible]:bg-surface-low">
      <MediaFrame image={project.cover} sizes={PORTFOLIO_CARD_SIZES} className="aspect-[4/5]" />
      <div className="flex min-w-0 flex-col gap-2">
        <ItemText
          titleId={titleId}
          item={{
            id: project.slug,
            image: project.cover,
            category: project.category,
            eyebrow: project.eyebrow,
            title: project.title,
            description: project.description,
          }}
        />
      </div>
      {/* Vacío a propósito: el foco global (contorno) rodea toda la tarjeta porque el enlace la cubre. */}
      <Link to={`/portafolio/${project.slug}`} aria-labelledby={titleId} className="absolute inset-0" />
    </div>
  )
}
