import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { PublicImage, PublicItem, PublicPage, PublicSection, SectionHeading } from '@/api/publicPage'

import { HomeSections } from './HomeSections'

/** Cabeceras publicadas (contenido editorial). */
const heading = (title: string | null, eyebrow: string | null = null, body: string | null = null): SectionHeading =>
  ({ eyebrow, title, body })
const SITE_NAME = 'Estudio de prueba'

const image = (id: string, extra: Partial<PublicImage> = {}): PublicImage => ({
  alt: `Alt ${id}`, width: 1600, height: 1067,
  sources: [{ type: 'image/avif', candidates: [{ url: `/api/public/media/files/${id}-a480`, width: 480 }] }],
  fallback: { type: 'image/jpeg', candidates: [480, 1280].map((w) => ({ url: `/api/public/media/files/${id}-${w}`, width: w })) },
  crop: null, mediaPosition: null, ...extra,
})
const item = (id: string, extra: Partial<PublicItem> = {}): PublicItem => ({
  id, image: image(id), category: 'Cobertura social', eyebrow: null, title: `Título ${id}`, description: null, ...extra,
})

function page(sections: PublicSection[]): PublicPage {
  return { pageKey: 'home', sections }
}

const catalog: PublicSection[] = [
  { sectionKey: 'hero', heading: heading('Titular del hero'), texts: [], items: [] },
  { sectionKey: 'editorial-note', heading: heading('Nota editorial'), texts: [{ id: 'n', eyebrow: null, title: null, body: 'Nota publicada' }], items: [] },
  { sectionKey: 'disciplines', heading: heading('Disciplinas', 'D'), texts: [], items: [] },
  { sectionKey: 'values', heading: heading('Valores'), texts: [{ id: 'v', eyebrow: 'Eyebrow', title: 'Honestidad', body: 'Uno\nDos' }], items: [] },
  { sectionKey: 'featured-selection', heading: heading('Selección', 'S'), texts: [], items: [] },
  { sectionKey: 'closing-cta', heading: heading('Cierre', 'C', 'Texto'), texts: [], items: [] },
]

function withItems(key: PublicSection['sectionKey'], items: PublicItem[], base = catalog): PublicSection[] {
  return base.map((s) => (s.sectionKey === key ? { ...s, items } : s))
}

/** Con tarjetas publicadas en Disciplinas y Selección destacada. */
const full = withItems('featured-selection', [item('f1'), item('f2')], withItems('disciplines', [item('d1')]))

describe('HomeSections', () => {
  it('pinta las secciones en el orden del catálogo', () => {
    const { container } = render(<HomeSections page={page(full)} siteName={SITE_NAME} />)

    const ids = [...container.querySelectorAll('section[data-section]')].map((s) => s.getAttribute('data-section'))
    expect(ids).toEqual(['hero', 'editorial-note', 'disciplines', 'values', 'featured-selection', 'closing-cta'])
  })

  it('el cierre va siempre al final aunque el catálogo lo ponga antes', () => {
    const reordered = [full[5], ...full.slice(0, 5)]
    const { container } = render(<HomeSections page={page(reordered)} siteName={SITE_NAME} />)

    expect(container.querySelectorAll('section[data-section]')[5]).toHaveAttribute('data-section', 'closing-cta')
  })

  it('no pinta secciones de texto sin bloques publicados', () => {
    const empty = catalog.map((s) => ({ ...s, texts: [] }))
    const { container } = render(<HomeSections page={page(empty)} siteName={SITE_NAME} />)

    expect(container.querySelector('[data-section="editorial-note"]')).toBeNull()
    expect(container.querySelector('[data-section="values"]')).toBeNull()
  })

  it('los campos opcionales vacíos no dejan huecos', () => {
    render(<HomeSections page={page(catalog)} siteName={SITE_NAME} />)

    const note = screen.getByText('Nota publicada').closest('[data-text-block]')!
    expect(note.children).toHaveLength(1)
  })

  it('el HTML del contenido se muestra como texto', () => {
    const evil = catalog.map((s) =>
      s.sectionKey === 'values' ? { ...s, texts: [{ id: 'x', eyebrow: null, title: null, body: '<img src=x onerror=alert(1)>' }] } : s,
    )
    const { container } = render(<HomeSections page={page(evil)} siteName={SITE_NAME} />)

    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })

  it('respeta los saltos de línea y parte las palabras largas', () => {
    render(<HomeSections page={page(catalog)} siteName={SITE_NAME} />)

    expect(screen.getByText(/Uno/)).toHaveClass('whitespace-pre-line', 'break-words')
  })

  it('el titular del hero es el único h1', () => {
    render(<HomeSections page={page(catalog)} siteName={SITE_NAME} />)

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Titular del hero')
  })

  it('el carrusel es una región etiquetada con controles accesibles y sin autoplay', () => {
    render(<HomeSections page={page(full)} siteName={SITE_NAME} />)

    const carousel = screen.getByRole('region', { name: 'Selección' })
    expect(within(carousel).getByRole('button', { name: 'Anterior' })).toBeInTheDocument()
    expect(within(carousel).getByRole('button', { name: 'Siguiente' })).toBeInTheDocument()
    expect(within(carousel).getAllByRole('group')).toHaveLength(2)
    expect(within(carousel).getAllByRole('group')[0]).toHaveAccessibleName('1 de 2')
  })

  it('el carrusel no usa semántica de lista (patrón APG)', () => {
    render(<HomeSections page={page(full)} siteName={SITE_NAME} />)

    const carousel = screen.getByRole('region', { name: 'Selección' })
    expect(within(carousel).queryByRole('list')).toBeNull()
    expect(within(carousel).queryAllByRole('listitem')).toHaveLength(0)
  })

  it('sin eyebrow no se pinta el párrafo ni queda margen superior en el título', () => {
    const bare = full.map((s) => (s.heading ? { ...s, heading: { ...s.heading, eyebrow: null } } : s))
    render(<HomeSections page={page(bare)} siteName={SITE_NAME} />)

    for (const name of ['Disciplinas', 'Selección']) {
      const heading = screen.getByRole('heading', { level: 2, name })
      expect(heading.parentElement!.querySelector('p')).toBeNull()
      expect(heading).not.toHaveClass('mt-3')
    }
  })

  it('pinta las imágenes publicadas con su texto alternativo y dimensiones', () => {
    render(<HomeSections page={page(withItems('disciplines', [item('d1')]))} siteName={SITE_NAME} />)

    const img = screen.getByRole('img', { name: 'Alt d1' })
    expect(img).toHaveAttribute('src', '/api/public/media/files/d1-1280')
    expect(img.getAttribute('srcset')).toBe('/api/public/media/files/d1-480 480w, /api/public/media/files/d1-1280 1280w')
    expect(img).toHaveAttribute('sizes', '100vw')
    expect(img).toHaveAttribute('width', '1600')
    expect(img).toHaveAttribute('height', '1067')
    expect(img).toHaveAttribute('loading', 'lazy')
    expect(screen.getByText('Cobertura social')).toBeInTheDocument()
  })

  it('sin elementos publicados no pinta Disciplinas ni Selección destacada', () => {
    const { container } = render(<HomeSections page={page(catalog)} siteName={SITE_NAME} />)

    expect(container.querySelector('[data-section="disciplines"]')).toBeNull()
    expect(container.querySelector('[data-section="featured-selection"]')).toBeNull()
  })

  it('Hero y Closing CTA se pintan sin imagen y sin huecos de texto', () => {
    const { container } = render(<HomeSections page={page(catalog)} siteName={SITE_NAME} />)

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Titular del hero')
    expect(container.querySelector('[data-section="hero"] img')).toBeNull()
    expect(container.querySelector('[data-section="hero"] [data-eyebrow]')).toBeNull()
    expect(screen.getByRole('heading', { name: 'Cierre' })).toBeInTheDocument()
  })

  it('el Hero muestra su información superior y carga su imagen sin lazy', () => {
    const hero = item('h', { eyebrow: 'Edición 2024', category: null, title: null })
    render(<HomeSections page={page(withItems('hero', [hero]))} siteName={SITE_NAME} />)

    expect(screen.getByText('Edición 2024')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Alt h' })).not.toHaveAttribute('loading', 'lazy')
  })

  it('cada hueco declara sus sizes y aplica el encuadre de su tipo', () => {
    const crop = { x: 0.2, y: 0, width: 0.5, height: 1 }
    const sections = withItems('hero', [item('h', { image: image('h', { mediaPosition: { x: 0.3, y: 0.6 } }) })],
      withItems('featured-selection', [item('f', { image: image('f', { crop }) })],
        withItems('disciplines', [item('d1', { image: image('d1', { crop }) }), item('d2', { image: image('d2', { crop }) })])))
    render(<HomeSections page={page(sections)} siteName={SITE_NAME} />)

    expect(screen.getByRole('img', { name: 'Alt h' })).toHaveAttribute('sizes', '(min-width: 768px) 50vw, 100vw')
    expect(screen.getByRole('img', { name: 'Alt h' }).style.objectPosition).toBe('30% 60%')
    expect(screen.getByRole('img', { name: 'Alt d1' })).toHaveAttribute('sizes', '(min-width: 640px) calc(50vw * 2), calc(100vw * 2)')
    expect(screen.getByRole('img', { name: 'Alt f' }).getAttribute('sizes')).toContain('calc(85vw * 2)')
    expect(screen.getByRole('img', { name: 'Alt d1' }).style.left).toBe('-40%')
  })

  it('la imagen del Closing CTA es la publicada', () => {
    render(<HomeSections page={page(withItems('closing-cta', [item('c', { category: null, title: null })]))} siteName={SITE_NAME} />)

    expect(screen.getByRole('img', { name: 'Alt c' })).toHaveAttribute('src', '/api/public/media/files/c-1280')
  })

  it('Valores y Disciplinas fijan una columna base (sin ancho min-content con palabras largas)', () => {
    const { container } = render(<HomeSections page={page(full)} siteName={SITE_NAME} />)

    for (const key of ['values', 'disciplines']) {
      const list = container.querySelector(`[data-section="${key}"] ul`)
      expect(list).toHaveClass('grid-cols-1')
      for (const li of list!.querySelectorAll(':scope > li')) expect(li).toHaveClass('min-w-0')
    }
  })

  it('TextBlock rompe palabras largas sin espacios', () => {
    const long = 'x'.repeat(200)
    const evilPage = page([{ sectionKey: 'values', heading: null, texts: [{ id: 'v', eyebrow: null, title: long, body: long }], items: [] }])
    const { container } = render(<HomeSections page={evilPage} siteName={SITE_NAME} />)

    const block = container.querySelector('[data-text-block]')!
    expect(block.querySelector('h3')).toHaveClass('[overflow-wrap:anywhere]')
    expect(block.querySelector('p')).toHaveClass('[overflow-wrap:anywhere]')
  })

  it('sin cabeceras publicadas el Hero usa el nombre del negocio y las secciones conservan un encabezado accesible', () => {
    const bare = full.map((s) => ({ ...s, heading: null }))
    const { container } = render(<HomeSections page={page(bare)} siteName={SITE_NAME} />)

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(SITE_NAME)
    expect(screen.getByRole('heading', { level: 2, name: 'Disciplinas' })).toHaveClass('sr-only')
    expect(screen.getByRole('region', { name: 'Selección destacada' })).toBeInTheDocument()
    // El cierre sin textos ni foto no se pinta.
    expect(container.querySelector('[data-section="closing-cta"]')).toBeNull()
  })

  it('el cierre muestra solo los textos publicados', () => {
    const sections = catalog.map((s) => (s.sectionKey === 'closing-cta' ? { ...s, heading: heading(null, null, 'Solo texto') } : s))
    const { container } = render(<HomeSections page={page(sections)} siteName={SITE_NAME} />)

    const cta = container.querySelector('[data-section="closing-cta"]')!
    expect(cta).toHaveAccessibleName('Cierre')
    expect(cta.querySelector('h2')).toBeNull()
    expect(screen.getByText('Solo texto')).toBeInTheDocument()
  })
})
