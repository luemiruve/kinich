import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { PublicSite } from '@/api/site'
import { EMPTY_SITE } from '@/content/site'

import { SiteFooter } from './SiteFooter'

const configured: PublicSite = {
  ...EMPTY_SITE,
  businessName: 'Estudio de prueba',
  tagline: 'Fotografía y video',
  contact: {
    email: 'contacto@example.com', whatsapp: '+15555550100', phone: '+15555550199', location: 'Ciudad de ejemplo',
    address: 'Calle 1\nInterior 2', openingHours: null,
  },
  socialLinks: [
    { platform: 'INSTAGRAM', label: null, url: 'https://www.instagram.com/ejemplo' },
    { platform: 'OTHER', label: 'Portafolio externo', url: 'https://portfolio.example.com' },
  ],
}

describe('SiteFooter', () => {
  it('una instalación vacía muestra el nombre provisional y nada más', () => {
    const { container } = render(<SiteFooter site={EMPTY_SITE} />)

    expect(screen.getByText('Kinich')).toBeInTheDocument()
    expect(screen.getByText('© Kinich')).toBeInTheDocument()
    expect(container.querySelector('address')).toBeNull()
    expect(screen.queryByRole('navigation')).toBeNull()
  })

  it('muestra el contacto y las redes configurados como enlaces', () => {
    render(<SiteFooter site={configured} />)

    expect(screen.getByRole('link', { name: 'contacto@example.com' })).toHaveAttribute('href', 'mailto:contacto@example.com')
    expect(screen.getByRole('link', { name: /WhatsApp/ })).toHaveAttribute('href', 'https://wa.me/15555550100')
    expect(screen.getByRole('link', { name: '+15555550199' })).toHaveAttribute('href', 'tel:+15555550199')
    expect(screen.getByText(/Calle 1/)).toHaveClass('whitespace-pre-line')
    const social = within(screen.getByRole('navigation', { name: 'Redes sociales' }))
    expect(social.getByRole('link', { name: 'Instagram' })).toHaveAttribute('rel', 'noopener noreferrer')
    expect(social.getByRole('link', { name: 'Portafolio externo' })).toHaveAttribute('href', 'https://portfolio.example.com')
    expect(screen.getByText('© Estudio de prueba')).toBeInTheDocument()
  })

  it('nunca crea enlaces que no sean https', () => {
    render(<SiteFooter site={{ ...EMPTY_SITE, socialLinks: [{ platform: 'X', label: null, url: 'javascript:alert(1)' }] }} />)

    expect(screen.queryByRole('link')).toBeNull()
  })
})
