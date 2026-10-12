import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { SiteLogo } from '@/api/site'

import { Logo } from './Logo'

const logo = (id: string, width = 240, height = 60): SiteLogo => ({ url: `/api/public/brand-assets/${id}`, width, height })

describe('Logo', () => {
  it('sin logotipo configurado muestra el nombre del negocio en texto', () => {
    render(<Logo name="Estudio de prueba" logo={null} logoOnDark={null} maxHeight={40} />)

    expect(screen.getByText('Estudio de prueba')).toBeInTheDocument()
    expect(screen.queryByRole('img')).toBeNull()
  })

  it('el SVG principal se usa como imagen, con el nombre de texto alternativo y medidas fijas (sin salto de layout)', () => {
    const { container } = render(<Logo name="Estudio de prueba" logo={logo('claro')} logoOnDark={null} maxHeight={40} />)

    const img = screen.getByRole('img', { name: 'Estudio de prueba' })
    expect(img.tagName).toBe('IMG')
    expect(img).toHaveAttribute('src', '/api/public/brand-assets/claro')
    expect(img).toHaveAttribute('width', '160') // 40 px de alto × 4:1 del viewBox
    expect(img).toHaveAttribute('height', '40')
    expect(img).toHaveClass('w-auto')
    // Nunca se inserta el SVG en el HTML ni se recolorea con máscaras.
    expect(container.querySelector('svg')).toBeNull()
    expect(container.innerHTML).not.toContain('mask')
  })

  it('sin variante oscura no hay <source>: un único SVG con prefers-color-scheme cubre ambos temas', () => {
    const { container } = render(<Logo name="Estudio" logo={logo('claro')} logoOnDark={null} maxHeight={40} />)

    expect(container.querySelector('source')).toBeNull()
  })

  it('con variante oscura el navegador la elige solo con HTML (source media), sin JavaScript', () => {
    const { container } = render(
      <Logo name="Estudio" logo={logo('claro')} logoOnDark={logo('oscuro', 120, 40)} maxHeight={40} />,
    )

    const dark = container.querySelector('picture > source')!
    expect(dark).toHaveAttribute('media', '(prefers-color-scheme: dark)')
    expect(dark).toHaveAttribute('type', 'image/svg+xml')
    expect(dark).toHaveAttribute('srcset', '/api/public/brand-assets/oscuro')
    // Sus propias medidas: si la variante tiene otra proporción, el hueco reservado ya es el correcto.
    expect(dark).toHaveAttribute('width', '120')
    expect(dark).toHaveAttribute('height', '40')
    expect(container.querySelector('picture > img')).toHaveAttribute('src', '/api/public/brand-assets/claro')
  })
})
