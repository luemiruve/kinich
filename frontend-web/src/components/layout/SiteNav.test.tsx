import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useNavigate } from 'react-router'
import { describe, expect, it } from 'vitest'

import { SiteNav } from './SiteNav'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <SiteNav />
    </MemoryRouter>,
  )
}

describe('SiteNav', () => {
  it('ofrece solo Inicio y Portafolio con nombre accesible', () => {
    renderAt('/')
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(nav).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Inicio' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'Portafolio' })).toHaveAttribute('href', '/portafolio')
    expect(screen.getAllByRole('link')).toHaveLength(2)
  })

  it('marca Inicio como activa solo en la portada', () => {
    renderAt('/')
    expect(screen.getByRole('link', { name: 'Inicio' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Portafolio' })).not.toHaveAttribute('aria-current')
  })

  it.each(['/portafolio', '/portafolio/boda-en-la-playa'])('marca Portafolio como activa en %s', (path) => {
    renderAt(path)
    expect(screen.getByRole('link', { name: 'Portafolio' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Inicio' })).not.toHaveAttribute('aria-current')
  })

  it('el botón Menú declara aria-expanded y aria-controls y alterna el panel', async () => {
    const user = userEvent.setup()
    renderAt('/')
    const button = screen.getByRole('button', { name: 'Menú' })
    expect(button).toHaveAttribute('aria-expanded', 'false')
    const panelId = button.getAttribute('aria-controls')
    expect(panelId).toBeTruthy()
    const panel = document.getElementById(panelId!)
    expect(panel).not.toBeNull()
    expect(panel).toContainElement(screen.getByRole('link', { name: 'Inicio' }))

    await user.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'true')
    await user.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'false')
  })

  it('Escape cierra el menú y devuelve el foco al botón', async () => {
    const user = userEvent.setup()
    renderAt('/')
    const button = screen.getByRole('button', { name: 'Menú' })
    await user.click(button)
    screen.getByRole('link', { name: 'Portafolio' }).focus()

    await user.keyboard('{Escape}')

    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(button).toHaveFocus()
  })

  it('navegar cierra el menú', async () => {
    const user = userEvent.setup()
    renderAt('/')
    const button = screen.getByRole('button', { name: 'Menú' })
    await user.click(button)
    await user.click(screen.getByRole('link', { name: 'Portafolio' }))

    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByRole('link', { name: 'Portafolio' })).toHaveAttribute('aria-current', 'page')
  })

  it('pulsar el enlace de la página actual también cierra el menú', async () => {
    const user = userEvent.setup()
    renderAt('/portafolio')
    const button = screen.getByRole('button', { name: 'Menú' })
    await user.click(button)
    await user.click(screen.getByRole('link', { name: 'Portafolio' }))

    expect(button).toHaveAttribute('aria-expanded', 'false')
  })

  it('el panel abierto se pinta por encima del contenido de la página', () => {
    renderAt('/')
    const panelId = screen.getByRole('button', { name: 'Menú' }).getAttribute('aria-controls')!
    expect(document.getElementById(panelId)).toHaveClass('z-20')
  })

  it('una navegación programática también cierra el menú', async () => {
    const user = userEvent.setup()
    function Go() {
      const navigate = useNavigate()
      return <button onClick={() => navigate('/portafolio')}>ir</button>
    }
    render(
      <MemoryRouter initialEntries={['/']}>
        <SiteNav />
        <Go />
      </MemoryRouter>,
    )
    const button = screen.getByRole('button', { name: 'Menú' })
    await user.click(button)
    await user.click(screen.getByRole('button', { name: 'ir' }))
    expect(button).toHaveAttribute('aria-expanded', 'false')
  })
})
