import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation, useNavigationType } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/api/http'
import type { PublicImage } from '@/api/publicPage'
import * as portfolioApi from '@/api/publicPortfolio'
import type { PublicPortfolioPage, PublicProjectCard } from '@/api/publicPortfolio'
import * as siteApi from '@/api/site'
import { EMPTY_SITE } from '@/content/site'

import { PortfolioPage } from './PortfolioPage'

vi.mock('@/api/publicPortfolio', () => ({ fetchPublicPortfolio: vi.fn() }))
vi.mock('@/api/site', () => ({ fetchPublicSite: vi.fn() }))

const cover: PublicImage = {
  alt: 'Portada',
  width: 1200,
  height: 800,
  sources: [],
  fallback: { type: 'image/jpeg', candidates: [{ url: '/m/a.jpg', width: 800 }] },
  crop: { x: 0.1, y: 0, width: 0.5, height: 1 },
  mediaPosition: null,
}

const project = (slug: string, title: string, category: string | null, extra: Partial<PublicProjectCard> = {}): PublicProjectCard => ({
  slug, title, category, eyebrow: null, description: null, cover, ...extra,
})

const page: PublicPortfolioPage = {
  pageHeader: { eyebrow: 'Trabajo', title: 'Nuestro portafolio', body: 'Historias reales' },
  closingCta: { eyebrow: null, title: 'Hablemos', body: null },
  categories: ['Bodas', 'Retratos'],
  projects: [
    project('elena-y-carlos', 'Elena y Carlos', 'Bodas', { eyebrow: 'Verano', description: 'Una boda en el campo' }),
    project('retrato-mar', 'Retrato al mar', 'Retratos'),
    project('boda-lucia', 'Boda de Lucía', 'Bodas'),
  ],
}

function Location() {
  const { search } = useLocation()
  return <output data-testid="search">{search}</output>
}

function renderAt(path = '/portafolio') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <PortfolioPage />
      <Location />
    </MemoryRouter>,
  )
}

describe('PortfolioPage', () => {
  beforeEach(() => {
    vi.mocked(siteApi.fetchPublicSite).mockResolvedValue({ ...EMPTY_SITE, businessName: 'Estudio de prueba' })
    vi.mocked(portfolioApi.fetchPublicPortfolio).mockReset().mockResolvedValue(page)
  })

  it('muestra un esqueleto mientras carga', () => {
    vi.mocked(portfolioApi.fetchPublicPortfolio).mockReturnValue(new Promise(() => {}))
    renderAt()
    expect(screen.getByRole('status', { name: 'Cargando contenido' })).toBeInTheDocument()
  })

  it('si falla muestra el mensaje y permite reintentar', async () => {
    vi.mocked(portfolioApi.fetchPublicPortfolio).mockRejectedValueOnce(new ApiError(0, 'Sin conexión.')).mockResolvedValue(page)
    renderAt()
    expect(await screen.findByRole('alert')).toHaveTextContent('Sin conexión.')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('link', { name: 'Elena y Carlos' })).toBeInTheDocument()
  })

  it('tiene un único h1 con el título publicado y el título del documento incluye el nombre', async () => {
    renderAt()
    expect(await screen.findByRole('heading', { level: 1, name: 'Nuestro portafolio' })).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    await waitFor(() => expect(document.title).toBe('Portafolio · Estudio de prueba'))
  })

  it('sin cabecera publicada el h1 es «Portafolio»', async () => {
    vi.mocked(portfolioApi.fetchPublicPortfolio).mockResolvedValue({ ...page, pageHeader: null })
    renderAt()
    expect(await screen.findByRole('heading', { level: 1, name: 'Portafolio' })).toBeInTheDocument()
  })

  it('cada tarjeta tiene un único enlace superpuesto a su detalle con el título como nombre accesible', async () => {
    renderAt()
    const link = await screen.findByRole('link', { name: 'Elena y Carlos' })
    expect(link).toHaveAttribute('href', '/portafolio/elena-y-carlos')
    expect(link).not.toHaveAttribute('aria-label')
    expect(link).toHaveClass('absolute', 'inset-0')
    const card = link.parentElement!
    expect(within(card).getAllByRole('link')).toHaveLength(1)
    // El contenido sigue en el documento (fuera del enlace): textos, título h3 y portada con su alt.
    expect(within(card).getByRole('heading', { level: 3, name: 'Elena y Carlos' })).toBeInTheDocument()
    expect(within(card).getByText('Una boda en el campo')).toBeInTheDocument()
    expect(within(card).getByRole('img', { name: 'Portada' })).toBeInTheDocument()
    expect(card).not.toHaveTextContent('Ver historia')
  })

  it('las tarjetas (h3) van tras un h2 «Proyectos» visualmente oculto', async () => {
    renderAt()
    const heading = await screen.findByRole('heading', { level: 2, name: 'Proyectos' })
    expect(heading).toHaveClass('sr-only')
    const card = screen.getByRole('heading', { level: 3, name: 'Elena y Carlos' })
    expect(heading.compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('cambiar de filtro reemplaza la entrada del historial', async () => {
    function NavigationType() {
      return <output data-testid="navigation">{useNavigationType()}</output>
    }
    render(
      <MemoryRouter initialEntries={['/portafolio']}>
        <PortfolioPage />
        <NavigationType />
      </MemoryRouter>,
    )
    await screen.findByRole('link', { name: 'Elena y Carlos' })
    await userEvent.setup().click(screen.getByRole('button', { name: 'Bodas' }))
    expect(screen.getByTestId('navigation')).toHaveTextContent('REPLACE')
  })

  it('la portada usa MediaFrame con su crop 4:5', async () => {
    renderAt()
    const link = await screen.findByRole('link', { name: 'Elena y Carlos' })
    const img = link.parentElement!.querySelector('img')!
    expect(img.style.width).toBe('200%')
    expect(img.parentElement!.parentElement).toHaveClass('aspect-[4/5]')
  })

  it('ofrece «Todos» y las categorías con aria-pressed', async () => {
    renderAt()
    await screen.findByRole('link', { name: 'Elena y Carlos' })
    expect(screen.getByRole('button', { name: 'Todos' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Bodas' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { name: 'Retratos' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('elegir una categoría filtra sin nueva petición y actualiza ?categoria=', async () => {
    renderAt()
    await screen.findByRole('link', { name: 'Elena y Carlos' })
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Retratos' }))

    expect(screen.getAllByRole('link', { name: /Retrato al mar|Elena|Boda/ })).toHaveLength(1)
    expect(screen.queryByRole('link', { name: 'Elena y Carlos' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retratos' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByTestId('search')).toHaveTextContent('?categoria=Retratos')

    await user.click(screen.getByRole('button', { name: 'Todos' }))
    expect(screen.getByRole('link', { name: 'Elena y Carlos' })).toBeInTheDocument()
    expect(screen.getByTestId('search')).toBeEmptyDOMElement()
    expect(portfolioApi.fetchPublicPortfolio).toHaveBeenCalledTimes(1)
  })

  it('?categoria= se lee sin distinguir mayúsculas', async () => {
    renderAt('/portafolio?categoria=bodas')
    await screen.findByRole('link', { name: 'Elena y Carlos' })
    expect(screen.getByRole('button', { name: 'Bodas' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByRole('link', { name: 'Retrato al mar' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Boda de Lucía' })).toBeInTheDocument()
  })

  it('una categoría desconocida en la URL muestra «Todos» (sin lista vacía)', async () => {
    renderAt('/portafolio?categoria=Eventos')
    await screen.findByRole('link', { name: 'Elena y Carlos' })
    expect(screen.getByRole('button', { name: 'Todos' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getAllByRole('link', { name: /Elena y Carlos|Retrato al mar|Boda de Lucía/ })).toHaveLength(3)
  })

  it('sin proyectos muestra un mensaje sobrio y no hay filtros', async () => {
    vi.mocked(portfolioApi.fetchPublicPortfolio).mockResolvedValue({ ...page, categories: [], projects: [] })
    renderAt()
    expect(await screen.findByText('Todavía no hay proyectos publicados.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Todos' })).not.toBeInTheDocument()
  })

  it('el cierre va al final del contenido, antes del pie', async () => {
    renderAt()
    const closing = (await screen.findByRole('heading', { name: 'Hablemos' })).closest('section')!
    const footer = document.querySelector('footer')!
    expect(closing.compareDocumentPosition(footer) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    const last = screen.getByRole('link', { name: 'Boda de Lucía' })
    expect(last.compareDocumentPosition(closing) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('sin cierre publicado no se pinta', async () => {
    vi.mocked(portfolioApi.fetchPublicPortfolio).mockResolvedValue({ ...page, closingCta: null })
    renderAt()
    await screen.findByRole('link', { name: 'Elena y Carlos' })
    expect(document.querySelector('[data-section="closing-cta"]')).toBeNull()
  })
})
