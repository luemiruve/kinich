import type { ReactNode } from 'react'

import type { PublicSite } from '@/api/site'

import { SiteFooter } from './SiteFooter'
import { SiteHeader } from './SiteHeader'

export function PublicLayout({ site, children }: { site: PublicSite; children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#contenido"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-10 focus:bg-surface focus:px-4 focus:py-3"
      >
        Saltar al contenido
      </a>
      <SiteHeader site={site} />
      <main id="contenido" tabIndex={-1} className="flex-1 focus:outline-none">
        {children}
      </main>
      <SiteFooter site={site} />
    </div>
  )
}
