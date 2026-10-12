import { Link } from 'react-router'

import type { PublicSite } from '@/api/site'
import { Logo } from '@/components/brand/Logo'
import { displayName } from '@/content/site'

/** Header mínimo: la marca de la instalación, enlazada a la portada. La navegación a otras páginas queda pendiente. */
export function SiteHeader({ site }: { site: PublicSite }) {
  return (
    <header className="border-b border-outline-variant px-4 py-2 sm:px-6 md:px-16">
      <Link to="/" className="flex min-h-11 w-fit max-w-full items-center">
        <Logo name={displayName(site)} logo={site.logo} logoOnDark={site.logoOnDark} maxHeight={40} className="h-8 sm:h-10" />
      </Link>
    </header>
  )
}
