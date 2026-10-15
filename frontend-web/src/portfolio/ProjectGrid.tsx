import type { PublicProjectCard } from '@/api/publicPortfolio'

import { ProjectCard } from './ProjectCard'

/** Rejilla de 1 / 2 / 3 columnas (móvil / sm / lg). */
export function ProjectGrid({ projects }: { projects: PublicProjectCard[] }) {
  return (
    <ul className="grid grid-cols-1 gap-6 px-4 sm:grid-cols-2 sm:px-6 md:px-16 lg:grid-cols-3">
      {projects.map((project) => (
        <li key={project.slug} className="min-w-0">
          <ProjectCard project={project} />
        </li>
      ))}
    </ul>
  )
}
