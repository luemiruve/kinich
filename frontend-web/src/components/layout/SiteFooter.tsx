import type { PublicSite } from '@/api/site'
import { displayName, socialLabel, whatsappUrl } from '@/content/site'

const linkClass = 'inline-flex min-h-11 items-center break-all underline decoration-secondary underline-offset-4 hover:text-secondary'

/** Pie con la identidad, el contacto y las redes configurados. Lo que no está configurado no se muestra. */
export function SiteFooter({ site }: { site: PublicSite }) {
  const name = displayName(site)
  const { email, whatsapp, phone, location, address, openingHours } = site.contact
  const hasContact = !!(email || whatsapp || phone || location || address || openingHours)
  // El backend solo admite https://; se comprueba otra vez antes de crear el enlace.
  const links = site.socialLinks.filter((link) => link.url.startsWith('https://'))
  return (
    <footer className="border-t border-outline-variant px-4 py-8 sm:px-6 md:px-16">
      <p className="font-serif text-lg break-words">{name}</p>
      {site.tagline && <p className="mt-2 max-w-2xl text-sm break-words text-on-surface-variant">{site.tagline}</p>}
      {(hasContact || links.length > 0) && (
        <div className="mt-6 grid gap-6 text-sm sm:grid-cols-2">
          {hasContact && (
            <address className="flex min-w-0 flex-col gap-1 not-italic text-on-surface-variant">
              {email && <a className={linkClass} href={`mailto:${email}`}>{email}</a>}
              {whatsapp && <a className={linkClass} href={whatsappUrl(whatsapp)}>WhatsApp {whatsapp}</a>}
              {phone && <a className={linkClass} href={`tel:${phone}`}>{phone}</a>}
              {location && <p className="break-words">{location}</p>}
              {address && <p className="whitespace-pre-line break-words">{address}</p>}
              {openingHours && <p className="whitespace-pre-line break-words">{openingHours}</p>}
            </address>
          )}
          {links.length > 0 && (
            <nav aria-label="Redes sociales" className="min-w-0">
              <ul className="flex flex-wrap gap-x-4">
                {links.map((link) => (
                  <li key={link.url}>
                    <a className={linkClass} href={link.url} rel="noopener noreferrer">{socialLabel(link)}</a>
                  </li>
                ))}
              </ul>
            </nav>
          )}
        </div>
      )}
      <p className="mt-6 text-sm text-on-surface-variant">© {name}</p>
    </footer>
  )
}
