import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/api/http'
import type { PublicImage } from '@/api/publicPage'
import * as portfolioApi from '@/api/publicPortfolio'
import type { PublicProject } from '@/api/publicPortfolio'
import * as siteApi from '@/api/site'
import { EMPTY_SITE } from '@/content/site'

import { PortfolioProjectPage } from './PortfolioProjectPage'

vi.mock('@/api/publicPortfolio', () => ({ fetchPublicProject: vi.fn() }))
vi.mock('@/api/site', () => ({ fetchPublicSite: vi.fn() }))

const image = (alt: string, width: number, height: number): PublicImage => ({
  alt,
  width,
  height,
  sources: [],
  fallback: { type: 'image/jpeg', candidates: [{ url: `/m/${alt}.jpg`, width }] },
  crop: null,
  mediaPosition: null,
})

const project: PublicProject = {
  slug: 'elena-y-carlos',
  title: 'Elena y Carlos',
  eyebrow: 'Verano',
  category: 'Bodas',
  description: 'Una boda en el campo',
  images: [
    { image: image('Primera', 1600, 900), caption: 'Llegada' },
    { image: image('Segunda', 900, 1600), caption: null },
    { image: image('Tercera', 1200, 1200), caption: 'Brindis' },
  ],
}

function renderAt(path = '/portafolio/elena-y-carlos') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/" element={<p>Inicio</p>} />
        <Route path="/portafolio/:slug" element={<PortfolioProjectPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('PortfolioProjectPage', () => {
  beforeEach(() => {
    vi.mocked(siteApi.fetchPublicSite).mockResolvedValue({ ...EMPTY_SITE, businessName: 'Estudio de prueba' })
    vi.mocked(portfolioApi.fetchPublicProject).mockReset().mockResolvedValue(project)
  })

  it('pide el proyecto por su slug', async () => {
    renderAt()
    await screen.findByRole('heading', { level: 1 })
    expect(vi.mocked(portfolioApi.fetchPublicProject).mock.calls[0][0]).toBe('elena-y-carlos')
  })

  it('«Volver al portafolio» enlaza a /portafolio', async () => {
    renderAt()
    const back = await screen.findByRole('link', { name: 'Volver al portafolio' })
    expect(back).toHaveAttribute('href', '/portafolio')
  })

  it('h1 = título, con categoría, eyebrow y descripción', async () => {
    renderAt()
    expect(await screen.findByRole('heading', { level: 1, name: 'Elena y Carlos' })).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(screen.getByText('Bodas')).toBeInTheDocument()
    expect(screen.getByText('Verano')).toBeInTheDocument()
    expect(screen.getByText('Una boda en el campo')).toBeInTheDocument()
  })

  it('categoría, eyebrow y descripción son opcionales y no dejan huecos', async () => {
    vi.mocked(portfolioApi.fetchPublicProject).mockResolvedValue({ ...project, category: null, eyebrow: null, description: null })
    renderAt()
    const h1 = await screen.findByRole('heading', { level: 1, name: 'Elena y Carlos' })
    expect(h1.parentElement!.children).toHaveLength(1)
  })

  it('la galería respeta el orden y las dimensiones originales, sin recorte', async () => {
    renderAt()
    await screen.findByRole('heading', { level: 1 })
    const imgs = screen.getAllByRole('img')
    expect(imgs.map((i) => i.getAttribute('alt'))).toEqual(['Primera', 'Segunda', 'Tercera'])
    expect(imgs.map((i) => [i.getAttribute('width'), i.getAttribute('height')])).toEqual([
      ['1600', '900'],
      ['900', '1600'],
      ['1200', '1200'],
    ])
    const frame = imgs[1].parentElement!.parentElement!
    expect(frame.style.aspectRatio.replace(/\s/g, '')).toBe('900/1600')
    expect(imgs[1].style.position).not.toBe('absolute')
  })

  it('figcaption solo cuando hay pie de foto', async () => {
    renderAt()
    await screen.findByRole('heading', { level: 1 })
    const figures = screen.getAllByRole('figure')
    expect(figures).toHaveLength(3)
    expect(within(figures[0]).getByText('Llegada').tagName).toBe('FIGCAPTION')
    expect(figures[1].querySelector('figcaption')).toBeNull()
    expect(within(figures[2]).getByText('Brindis').tagName).toBe('FIGCAPTION')
  })

  it('el título del documento es el del proyecto con el nombre del sitio', async () => {
    renderAt()
    await waitFor(() => expect(document.title).toBe('Elena y Carlos · Estudio de prueba'))
  })

  it('un 404 muestra «Proyecto no encontrado» con enlace al portafolio, sin redirigir a /', async () => {
    vi.mocked(portfolioApi.fetchPublicProject).mockRejectedValue(new ApiError(404, 'No encontrado.'))
    renderAt('/portafolio/no-existe')
    expect(await screen.findByRole('heading', { level: 1, name: 'Proyecto no encontrado' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver al portafolio' })).toHaveAttribute('href', '/portafolio')
    expect(screen.queryByText('Inicio', { selector: 'p' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })

  it('otro error muestra el mensaje y permite reintentar', async () => {
    vi.mocked(portfolioApi.fetchPublicProject).mockRejectedValueOnce(new ApiError(500, 'Fallo.')).mockResolvedValue(project)
    renderAt()
    expect(await screen.findByRole('alert')).toHaveTextContent('Fallo.')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Elena y Carlos' })).toBeInTheDocument()
  })
})
