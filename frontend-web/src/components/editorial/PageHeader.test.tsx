import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { PageHeader } from './PageHeader'

describe('PageHeader', () => {
  it('pinta eyebrow, h1, hairline y cuerpo', () => {
    const { container } = render(
      <PageHeader heading={{ eyebrow: 'Trabajo', title: 'Nuestras historias', body: 'Una selección.' }} fallbackTitle="Portafolio" />,
    )
    expect(screen.getByRole('heading', { level: 1, name: 'Nuestras historias' })).toBeInTheDocument()
    expect(screen.getByText('Trabajo')).toBeInTheDocument()
    expect(screen.getByText('Una selección.')).toHaveClass('italic', 'font-serif')
    expect(container.querySelector('[data-hairline]')).not.toBeNull()
  })

  it('sin cabecera publicada usa el título de respaldo', () => {
    const { container } = render(<PageHeader heading={null} fallbackTitle="Portafolio" />)
    expect(screen.getByRole('heading', { level: 1, name: 'Portafolio' })).toBeInTheDocument()
    expect(container.querySelector('[data-eyebrow]')).toBeNull()
    expect(container.querySelector('[data-hairline]')).toBeNull()
  })

  it('con título vacío usa el respaldo y conserva el cuerpo', () => {
    render(<PageHeader heading={{ eyebrow: null, title: null, body: 'Solo cuerpo' }} fallbackTitle="Portafolio" />)
    expect(screen.getByRole('heading', { level: 1, name: 'Portafolio' })).toBeInTheDocument()
    expect(screen.getByText('Solo cuerpo')).toBeInTheDocument()
  })
})
