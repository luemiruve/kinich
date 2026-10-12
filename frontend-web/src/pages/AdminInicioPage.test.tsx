import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as adminApi from '@/api/adminPage'
import type { AdminItem, AdminPage, AdminSection, AdminTextBlock } from '@/api/adminPage'
import * as mediaApi from '@/api/adminMedia'
import * as authApi from '@/api/auth'
import * as siteApi from '@/api/site'
import { ApiError } from '@/api/http'
import { App } from '@/App'
import { EMPTY_SITE } from '@/content/site'

vi.mock('@/api/auth', () => ({ fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('@/api/publicPage', () => ({ fetchPublicHome: vi.fn() }))
vi.mock('@/api/site', () => ({ fetchPublicSite: vi.fn() }))
vi.mock('@/api/adminPage', () => ({
  fetchAdminHome: vi.fn(), createTextBlock: vi.fn(), updateTextBlock: vi.fn(), setTextBlockVisibility: vi.fn(),
  deleteTextBlock: vi.fn(), reorderTextBlocks: vi.fn(), publishSection: vi.fn(),
  createItem: vi.fn(), updateItem: vi.fn(), setItemVisibility: vi.fn(), deleteItem: vi.fn(), reorderItems: vi.fn(),
  updateSectionHeading: vi.fn(),
}))
vi.mock('@/api/adminMedia', () => ({ uploadMedia: vi.fn(), fetchMedia: vi.fn(), fetchCategories: vi.fn(), createCategory: vi.fn() }))

const api = vi.mocked(adminApi)

const block = (id: string, position: number, extra: Partial<AdminTextBlock> = {}): AdminTextBlock => ({
  id, eyebrow: null, title: `Título ${id}`, body: `Texto ${id}`, hidden: false, position, version: 1,
  status: 'DRAFT', modified: false, ...extra,
})

const section = (sectionKey: AdminSection['sectionKey'], extra: Partial<AdminSection> = {}): AdminSection => ({
  sectionKey, textEditable: false, maxBlocks: null, itemEditable: false, maxItems: null, itemFields: [],
  requiredItemFields: [], itemFraming: null, headingFields: [], heading: { eyebrow: null, title: null, body: null }, revision: 0, publishedRevision: 0, hasUnpublishedChanges: false, publishedAt: null,
  texts: [], items: [], ...extra,
})

const CARD_FIELDS: AdminSection['itemFields'] = ['eyebrow', 'categoryId', 'title', 'description']

const adminItem = (id: string, position: number, extra: Partial<AdminItem> = {}): AdminItem => ({
  id, image: {
    alt: `Alt ${id}`, width: 1600, height: 1067,
    sources: [{ type: 'image/avif', candidates: [{ url: `/api/admin/media/files/a-${id}`, width: 480 }] }],
    fallback: { type: 'image/jpeg', candidates: [{ url: `/api/admin/media/files/j-${id}`, width: 480 }] },
    crop: { x: 0, y: 0, width: 853.6 / 1600, height: 1 }, mediaPosition: null,
  }, mediaId: `m-${id}`,
  category: null, eyebrow: null, title: `Título ${id}`, description: null, hidden: false, position, version: 1,
  status: 'DRAFT', modified: false, ...extra,
})

const media = (id: string) => ({
  id, contentType: 'image/jpeg', width: 1600, height: 1067, sizeBytes: 1000, createdAt: '2026-10-10T10:00:00Z',
  renditions: {
    sources: [{ type: 'image/avif', candidates: [{ url: `/api/admin/media/files/a-${id}`, width: 480 }] }],
    fallback: { type: 'image/jpeg', candidates: [{ url: `/api/admin/media/files/j-${id}`, width: 480 }] },
  },
})

function adminPage(values: AdminTextBlock[] = [block('a', 1), block('b', 2)], note: AdminTextBlock[] = []): AdminPage {
  return {
    pageKey: 'home',
    sections: [
      section('hero', { itemEditable: true, maxItems: 1, itemFields: ['eyebrow'], itemFraming: 'FOCAL_POINT' }),
      section('editorial-note', { textEditable: true, maxBlocks: 1, texts: note }),
      section('disciplines', { itemEditable: true, maxItems: 4, itemFields: CARD_FIELDS, requiredItemFields: ['title'],
        itemFraming: 'CROP_4_5' }),
      section('values', { textEditable: true, revision: 5, publishedRevision: 2, hasUnpublishedChanges: true, texts: values }),
      section('featured-selection', { itemEditable: true, itemFields: CARD_FIELDS, requiredItemFields: ['title'],
        itemFraming: 'CROP_4_5' }),
      section('closing-cta', { itemEditable: true, maxItems: 1, itemFraming: 'FOCAL_POINT' }),
    ],
  }
}

function adminPageWith(key: AdminSection['sectionKey'], items: AdminItem[], extra: Partial<AdminSection> = {}): AdminPage {
  const page = adminPage()
  return { ...page, sections: page.sections.map((s) => (s.sectionKey === key ? { ...s, items, ...extra } : s)) }
}

async function region(name: string) {
  return within(await screen.findByRole('region', { name }))
}

function renderPage() {
  return render(<MemoryRouter initialEntries={['/admin/inicio']}><App /></MemoryRouter>)
}

async function valuesSection() {
  return within(await screen.findByRole('region', { name: 'Valores' }))
}

describe('/admin/inicio', () => {
  beforeEach(() => {
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue({ email: 'admin@example.com', fullName: 'Administración' })
    vi.mocked(siteApi.fetchPublicSite).mockResolvedValue({ ...EMPTY_SITE, businessName: 'Estudio de prueba' })
    api.fetchAdminHome.mockResolvedValue(adminPage())
    vi.mocked(mediaApi.fetchMedia).mockResolvedValue([media('m1')])
    vi.mocked(mediaApi.fetchCategories).mockResolvedValue([{ id: 'c1', label: 'Cobertura social' }])
  })

  it('exige sesión', async () => {
    vi.mocked(authApi.fetchCurrentUser).mockRejectedValue(new ApiError(401, 'Debes iniciar sesión.'))
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Acceso al Estudio' })).toBeInTheDocument()
  })

  it('todas las secciones de Inicio son editables', async () => {
    renderPage()
    for (const name of ['Hero', 'Nota editorial', 'Disciplinas', 'Valores', 'Selección destacada', 'Cierre']) {
      expect(await screen.findByRole('region', { name })).toBeInTheDocument()
    }
    expect(screen.queryByText('Contenido provisional — pendiente de medios')).toBeNull()
  })

  it('crear una disciplina elige imagen, texto alternativo, categoría y título', async () => {
    const user = userEvent.setup()
    api.createItem.mockResolvedValue({} as never)
    renderPage()
    const disciplines = await region('Disciplinas')
    await user.click(disciplines.getByRole('button', { name: 'Nuevo elemento' }))
    await user.click(disciplines.getByRole('button', { name: 'Elegir imagen' }))
    await user.click(await screen.findByRole('button', { name: /Imagen 1600 × 1067/ }))
    await user.type(disciplines.getByLabelText(/Texto alternativo/), 'Mesa al aire libre')
    await user.selectOptions(await disciplines.findByLabelText(/^Categoría/), 'c1')
    await user.type(disciplines.getByLabelText(/^Título/), 'Eventos sociales')
    await user.click(disciplines.getByRole('button', { name: 'Guardar borrador' }))
    expect(api.createItem).toHaveBeenCalledWith('disciplines', {
      mediaId: 'm1', imageAlt: 'Mesa al aire libre', categoryId: 'c1', eyebrow: '', title: 'Eventos sociales', description: '',
      crop: null, mediaPosition: null,
    })
  })

  it('el cierre solo pide imagen y texto alternativo', async () => {
    const user = userEvent.setup()
    renderPage()
    const closing = await region('Cierre')
    await user.click(closing.getByRole('button', { name: 'Nuevo elemento' }))
    expect(closing.getByLabelText(/Texto alternativo/)).toBeInTheDocument()
    expect(closing.queryByLabelText(/Título/)).toBeNull()
    expect(closing.queryByLabelText(/Categoría/)).toBeNull()
  })

  it('con 4 disciplinas no se puede añadir otra', async () => {
    const four = [1, 2, 3, 4].map((n) => adminItem(`d${n}`, n))
    api.fetchAdminHome.mockResolvedValue(adminPageWith('disciplines', four))
    renderPage()
    expect((await region('Disciplinas')).getByRole('button', { name: 'Nuevo elemento' })).toBeDisabled()
  })

  it('sin imagen elegida no se envía el formulario', async () => {
    const user = userEvent.setup()
    renderPage()
    const hero = await region('Hero')
    await user.click(hero.getByRole('button', { name: 'Nuevo elemento' }))
    await user.type(hero.getByLabelText(/Texto alternativo/), 'Alt')
    await user.click(hero.getByRole('button', { name: 'Guardar borrador' }))
    expect(api.createItem).not.toHaveBeenCalled()
    expect(hero.getByText('Elige una imagen')).toBeInTheDocument()
  })

  it('editar un elemento envía su versión y conserva la imagen', async () => {
    const user = userEvent.setup()
    api.updateItem.mockResolvedValue({} as never)
    api.fetchAdminHome.mockResolvedValue(adminPageWith('featured-selection', [adminItem('f1', 1, { version: 7 })]))
    renderPage()
    const featured = await region('Selección destacada')
    await user.click(featured.getByRole('button', { name: 'Editar «Título f1»' }))
    await user.clear(featured.getByLabelText(/^Título/))
    await user.type(featured.getByLabelText(/^Título/), 'Nuevo')
    await user.click(featured.getByRole('button', { name: 'Guardar borrador' }))
    expect(api.updateItem).toHaveBeenCalledWith('featured-selection', 'f1', {
      mediaId: 'm-f1', imageAlt: 'Alt f1', categoryId: null, eyebrow: '', title: 'Nuevo', description: '',
      crop: { x: 0, y: 0, width: 853.6 / 1600, height: 1 }, mediaPosition: null,
    }, 7)
  })

  it('el encuadre 4:5 elegido se envía con el elemento', async () => {
    const user = userEvent.setup()
    api.createItem.mockResolvedValue({} as never)
    renderPage()
    const disciplines = await region('Disciplinas')
    await user.click(disciplines.getByRole('button', { name: 'Nuevo elemento' }))
    await user.click(disciplines.getByRole('button', { name: 'Elegir imagen' }))
    await user.click(await screen.findByRole('button', { name: /Imagen 1600 × 1067/ }))
    fireEvent.change(disciplines.getByRole('slider', { name: 'Posición horizontal' }), { target: { value: '0' } })
    await user.type(disciplines.getByLabelText(/Texto alternativo/), 'Alt')
    await user.type(disciplines.getByLabelText(/^Título/), 'T')
    await user.click(disciplines.getByRole('button', { name: 'Guardar borrador' }))
    const sent = api.createItem.mock.calls[0][1]
    expect(sent.crop!.x).toBe(0)
    expect((sent.crop!.width * 1600) / (sent.crop!.height * 1067)).toBeCloseTo(0.8, 6)
    expect(sent.mediaPosition).toBeNull()
  })

  it('el Hero se encuadra con un punto focal', async () => {
    const user = userEvent.setup()
    api.createItem.mockResolvedValue({} as never)
    renderPage()
    const hero = await region('Hero')
    await user.click(hero.getByRole('button', { name: 'Nuevo elemento' }))
    await user.click(hero.getByRole('button', { name: 'Elegir imagen' }))
    await user.click(await screen.findByRole('button', { name: /Imagen 1600 × 1067/ }))
    expect(hero.queryByRole('slider', { name: 'Acercar' })).toBeNull()
    fireEvent.change(hero.getByRole('slider', { name: 'Punto focal vertical' }), { target: { value: '30' } })
    await user.type(hero.getByLabelText(/Texto alternativo/), 'Alt')
    await user.click(hero.getByRole('button', { name: 'Guardar borrador' }))
    expect(api.createItem.mock.calls[0][1]).toMatchObject({ crop: null, mediaPosition: { x: 0.5, y: 0.3 } })
  })

  it('publicar una sección de elementos envía su revisión', async () => {
    const user = userEvent.setup()
    api.fetchAdminHome.mockResolvedValue(adminPageWith('featured-selection', [adminItem('f1', 1)], { revision: 3, hasUnpublishedChanges: true }))
    renderPage()
    await user.click((await region('Selección destacada')).getByRole('button', { name: 'Publicar sección' }))
    expect(api.publishSection).toHaveBeenCalledWith('featured-selection', 3)
  })

  it('la previsualización muestra el borrador con las URL de administración', async () => {
    const user = userEvent.setup()
    api.fetchAdminHome.mockResolvedValue(adminPageWith('disciplines', [adminItem('d1', 1), adminItem('d2', 2, { hidden: true })]))
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Previsualizar' }))
    expect(screen.getByRole('img', { name: 'Alt d1' })).toHaveAttribute('src', '/api/admin/media/files/j-d1')
    expect(screen.queryByRole('img', { name: 'Alt d2' })).toBeNull()
  })

  it('guardar un nuevo valor crea un borrador y recarga, sin publicar', async () => {
    const user = userEvent.setup()
    api.createTextBlock.mockResolvedValue(block('c', 3))
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Nuevo elemento' }))
    await user.type(values.getByLabelText('Texto'), 'Honestidad')
    await user.click(values.getByRole('button', { name: 'Guardar borrador' }))

    expect(api.createTextBlock).toHaveBeenCalledWith('values', { eyebrow: '', title: '', body: 'Honestidad' })
    expect(api.publishSection).not.toHaveBeenCalled()
    expect(api.fetchAdminHome).toHaveBeenCalledTimes(2)
    expect(await screen.findByRole('status', { name: 'Avisos' })).toHaveTextContent('Borrador guardado')
  })

  it('la nota editorial no admite un segundo bloque', async () => {
    api.fetchAdminHome.mockResolvedValue(adminPage(undefined, [block('n', 1)]))
    renderPage()
    const note = within(await screen.findByRole('region', { name: 'Nota editorial' }))
    expect(note.getByRole('button', { name: 'Nuevo elemento' })).toBeDisabled()
  })

  it('editar envía la versión cargada', async () => {
    const user = userEvent.setup()
    api.updateTextBlock.mockResolvedValue(block('a', 1))
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Editar «Título a»' }))
    const body = values.getByLabelText('Texto')
    await user.clear(body)
    await user.type(body, 'Nuevo')
    await user.click(values.getByRole('button', { name: 'Guardar borrador' }))

    expect(api.updateTextBlock).toHaveBeenCalledWith('values', 'a', { eyebrow: '', title: 'Título a', body: 'Nuevo' }, 1)
  })

  it('previsualizar muestra el borrador sin ocultos ni controles', async () => {
    const user = userEvent.setup()
    api.fetchAdminHome.mockResolvedValue(adminPage([block('a', 1), block('x', 2, { hidden: true })]))
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Previsualizar' }))

    expect(screen.getByText('Texto a')).toBeInTheDocument()
    expect(screen.queryByText('Texto x')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Nuevo elemento' })).toBeNull()
  })

  it('publicar envía la revisión previsualizada y está separado de guardar', async () => {
    const user = userEvent.setup()
    api.publishSection.mockResolvedValue(undefined)
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Publicar sección' }))

    expect(api.publishSection).toHaveBeenCalledWith('values', 5)
    expect(await screen.findByRole('status', { name: 'Avisos' })).toHaveTextContent('Sección publicada')
  })

  it('sin cambios pendientes no se puede publicar', async () => {
    renderPage()
    const note = within(await screen.findByRole('region', { name: 'Nota editorial' }))
    expect(note.getByRole('button', { name: 'Publicar sección' })).toBeDisabled()
  })

  it('un conflicto de versión avisa y recarga', async () => {
    const user = userEvent.setup()
    api.publishSection.mockRejectedValue(new ApiError(409, 'El contenido cambió.', {}, 'VERSION_CONFLICT'))
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Publicar sección' }))

    expect(await screen.findByRole('status', { name: 'Avisos' })).toHaveTextContent('El contenido cambió')
    expect(api.fetchAdminHome).toHaveBeenCalledTimes(2)
  })

  it('los errores 400 se muestran en el campo', async () => {
    const user = userEvent.setup()
    api.createTextBlock.mockRejectedValue(new ApiError(400, 'Datos inválidos', { body: 'contiene caracteres no permitidos' }, 'VALIDATION_FAILED'))
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Nuevo elemento' }))
    await user.type(values.getByLabelText('Texto'), 'x')
    await user.click(values.getByRole('button', { name: 'Guardar borrador' }))

    expect(await values.findByText('contiene caracteres no permitidos')).toBeInTheDocument()
    expect(values.getByLabelText('Texto')).toHaveAttribute('aria-invalid', 'true')
  })

  it('eliminar pide confirmación', async () => {
    const user = userEvent.setup()
    api.deleteTextBlock.mockResolvedValue(undefined)
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Eliminar «Título a»' }))
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(api.deleteTextBlock).not.toHaveBeenCalled()

    await user.click(values.getByRole('button', { name: 'Eliminar «Título a»' }))
    await user.click(screen.getByRole('button', { name: 'Eliminar definitivamente' }))
    expect(api.deleteTextBlock).toHaveBeenCalledWith('values', 'a', 1)
  })

  it('bajar un valor envía el nuevo orden con la revisión', async () => {
    const user = userEvent.setup()
    api.reorderTextBlocks.mockResolvedValue(undefined)
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Bajar «Título a»' }))

    expect(api.reorderTextBlocks).toHaveBeenCalledWith('values', 5, ['b', 'a'])
  })

  it('ocultar envía la visibilidad con la versión', async () => {
    const user = userEvent.setup()
    api.setTextBlockVisibility.mockResolvedValue(block('a', 1, { hidden: true }))
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Ocultar «Título a»' }))

    expect(api.setTextBlockVisibility).toHaveBeenCalledWith('values', 'a', true, 1)
  })

  it('el contador respeta los límites técnicos y cuenta emojis como en el servidor', async () => {
    const user = userEvent.setup()
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Nuevo elemento' }))
    expect(values.getByLabelText('Texto')).toHaveAttribute('maxLength', '2000')
    await user.type(values.getByLabelText('Texto'), '📷')
    expect(values.getByText('2 / 2000')).toBeInTheDocument()
  })

  it('con un formulario abierto se deshabilitan las demás acciones de la sección', async () => {
    const user = userEvent.setup()
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Editar «Título a»' }))

    expect(values.getByRole('button', { name: 'Subir «Título b»' })).toBeDisabled()
    expect(values.getByRole('button', { name: 'Ocultar «Título b»' })).toBeDisabled()
    expect(values.getByRole('button', { name: 'Eliminar «Título b»' })).toBeDisabled()
    expect(values.getByRole('button', { name: 'Nuevo elemento' })).toBeDisabled()
    expect(values.getByRole('button', { name: 'Publicar sección' })).toBeDisabled()
  })

  it('un 409 al guardar cierra el formulario, avisa y recarga', async () => {
    const user = userEvent.setup()
    api.updateTextBlock.mockRejectedValue(new ApiError(409, 'El contenido cambió.', {}, 'VERSION_CONFLICT'))
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Editar «Título a»' }))
    await user.click(values.getByRole('button', { name: 'Guardar borrador' }))

    expect(await screen.findByRole('status', { name: 'Avisos' })).toHaveTextContent('El contenido cambió')
    expect(values.queryByLabelText('Texto')).toBeNull()
    expect(api.fetchAdminHome).toHaveBeenCalledTimes(2)
  })

  it('el error 400 del título se asocia a su campo', async () => {
    const user = userEvent.setup()
    api.createTextBlock.mockRejectedValue(new ApiError(400, 'Datos inválidos', { title: 'título no válido' }, 'VALIDATION_FAILED'))
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Nuevo elemento' }))
    await user.type(values.getByLabelText('Texto'), 'x')
    await user.click(values.getByRole('button', { name: 'Guardar borrador' }))

    const input = await values.findByLabelText('Título (opcional)')
    const error = await values.findByText('título no válido')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', error.id)
  })

  it('al cancelar la confirmación el foco vuelve al botón Eliminar', async () => {
    const user = userEvent.setup()
    renderPage()
    const values = await valuesSection()
    const trigger = values.getByRole('button', { name: 'Eliminar «Título a»' })

    await user.click(trigger)
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(trigger).toHaveFocus()
  })

  it('el velo del diálogo oscurece en ambos modos (token feature-panel)', async () => {
    const user = userEvent.setup()
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Eliminar «Título a»' }))

    const dialog = screen.getByRole('alertdialog', { hidden: true })
    expect(dialog.className).toContain('backdrop:bg-feature-panel/')
    expect(dialog.className).not.toContain('backdrop:bg-on-surface')
  })

  it('Escape (evento cancel) cancela sin eliminar', async () => {
    const user = userEvent.setup()
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Eliminar «Título a»' }))
    fireEvent(screen.getByRole('alertdialog', { hidden: true }), new Event('cancel'))

    expect(screen.queryByRole('alertdialog', { hidden: true })).toBeNull()
    expect(api.deleteTextBlock).not.toHaveBeenCalled()
  })

  it('los botones de subir y bajar miden al menos 44px de ancho', async () => {
    renderPage()
    const values = await valuesSection()
    expect(values.getByRole('button', { name: 'Bajar «Título a»' })).toHaveClass('min-w-11')
    expect(values.getByRole('button', { name: 'Subir «Título b»' })).toHaveClass('min-w-11')
  })

  it('en previsualización hay un único encabezado de nivel 1', async () => {
    const user = userEvent.setup()
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'Editar Inicio' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Previsualizar' }))

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
  })

  describe('foco', () => {
    it('al abrir el formulario de alta o de edición se enfoca su primer campo', async () => {
      const user = userEvent.setup()
      renderPage()
      const values = await valuesSection()

      await user.click(values.getByRole('button', { name: 'Nuevo elemento' }))
      expect(values.getByLabelText('Encabezado (opcional)')).toHaveFocus()
      await user.click(values.getByRole('button', { name: 'Cancelar' }))

      await user.click(values.getByRole('button', { name: 'Editar «Título a»' }))
      expect(values.getByLabelText('Encabezado (opcional)')).toHaveFocus()
    })

    it('al cancelar la edición el foco vuelve a su botón Editar', async () => {
      const user = userEvent.setup()
      renderPage()
      const values = await valuesSection()

      await user.click(values.getByRole('button', { name: 'Editar «Título b»' }))
      await user.click(values.getByRole('button', { name: 'Cancelar' }))

      expect(values.getByRole('button', { name: 'Editar «Título b»' })).toHaveFocus()
    })

    it('al cancelar el alta el foco vuelve a «Nuevo elemento»', async () => {
      const user = userEvent.setup()
      renderPage()
      const values = await valuesSection()

      await user.click(values.getByRole('button', { name: 'Nuevo elemento' }))
      await user.click(values.getByRole('button', { name: 'Cancelar' }))

      expect(values.getByRole('button', { name: 'Nuevo elemento' })).toHaveFocus()
    })

    it('al guardar la edición el foco vuelve a su botón Editar', async () => {
      const user = userEvent.setup()
      api.updateTextBlock.mockResolvedValue(block('a', 1))
      renderPage()
      const values = await valuesSection()

      await user.click(values.getByRole('button', { name: 'Editar «Título a»' }))
      await user.click(values.getByRole('button', { name: 'Guardar borrador' }))

      await waitFor(() => expect(values.getByRole('button', { name: 'Editar «Título a»' })).toHaveFocus())
    })

    it('si el bloque ya no existe tras un 409 el foco pasa al encabezado de la sección', async () => {
      const user = userEvent.setup()
      api.updateTextBlock.mockRejectedValue(new ApiError(409, 'El contenido cambió.', {}, 'VERSION_CONFLICT'))
      renderPage()
      const values = await valuesSection()
      api.fetchAdminHome.mockResolvedValue(adminPage([block('b', 1)]))

      await user.click(values.getByRole('button', { name: 'Editar «Título a»' }))
      await user.click(values.getByRole('button', { name: 'Guardar borrador' }))

      await waitFor(() => expect(values.getByRole('heading', { level: 2, name: 'Valores' })).toHaveFocus())
    })
  })

  it('con un formulario abierto se deshabilitan Editar de los demás bloques y Previsualizar', async () => {
    const user = userEvent.setup()
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Editar «Título a»' }))

    expect(values.getByRole('button', { name: 'Editar «Título b»' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Previsualizar' })).toBeDisabled()

    await user.click(values.getByRole('button', { name: 'Cancelar' }))
    expect(screen.getByRole('button', { name: 'Previsualizar' })).toBeEnabled()
  })

  it('mientras una escritura está en curso las acciones de la sección quedan deshabilitadas', async () => {
    const user = userEvent.setup()
    let finish: () => void = () => {}
    api.reorderTextBlocks.mockReturnValue(new Promise<undefined>((resolve) => { finish = () => resolve(undefined) }))
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Bajar «Título a»' }))

    expect(values.getByRole('button', { name: 'Bajar «Título a»' })).toBeDisabled()
    expect(values.getByRole('button', { name: 'Editar «Título b»' })).toBeDisabled()
    expect(values.getByRole('button', { name: 'Nuevo elemento' })).toBeDisabled()
    expect(values.getByRole('button', { name: 'Publicar sección' })).toBeDisabled()

    finish()
    await waitFor(() => expect(values.getByRole('button', { name: 'Bajar «Título a»' })).toBeEnabled())
  })

  it('dos avisos iguales seguidos cambian el contenido de la región para re-anunciarse', async () => {
    const user = userEvent.setup()
    api.publishSection.mockResolvedValue(undefined)
    renderPage()
    const values = await valuesSection()
    const status = await screen.findByRole('status', { name: 'Avisos' })

    await user.click(values.getByRole('button', { name: 'Publicar sección' }))
    await waitFor(() => expect(status).toHaveTextContent('Sección publicada'))
    const first = status.textContent
    await waitFor(() => expect(values.getByRole('button', { name: 'Publicar sección' })).toBeEnabled())
    await user.click(values.getByRole('button', { name: 'Publicar sección' }))

    await waitFor(() => expect(status.textContent).not.toBe(first))
    expect(status).toHaveTextContent('Sección publicada')
  })

  it('una escritura con la sesión caducada (401) lleva al login', async () => {
    const user = userEvent.setup()
    api.reorderTextBlocks.mockRejectedValue(new ApiError(401, 'Debes iniciar sesión.'))
    renderPage()
    const values = await valuesSection()

    await user.click(values.getByRole('button', { name: 'Bajar «Título a»' }))

    expect(await screen.findByRole('heading', { name: 'Acceso al Estudio' })).toBeInTheDocument()
  })

  it('cargar Inicio con la sesión caducada (401) lleva al login', async () => {
    api.fetchAdminHome.mockRejectedValue(new ApiError(401, 'Debes iniciar sesión.'))
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Acceso al Estudio' })).toBeInTheDocument()
  })

  it('publica la altura de la cabecera sticky como --kn-sticky-header y la limpia al desmontar', async () => {
    const original = Element.prototype.getBoundingClientRect
    Element.prototype.getBoundingClientRect = function () {
      return { height: this.tagName === 'HEADER' ? 61 : 0, width: 0, top: 0, left: 0, right: 0, bottom: 0, x: 0, y: 0, toJSON: () => ({}) }
    }
    try {
      const view = renderPage()
      await screen.findByRole('heading', { name: 'Editar Inicio' })
      await waitFor(() => expect(document.documentElement.style.getPropertyValue('--kn-sticky-header')).toBe('61px'))

      view.unmount()
      expect(document.documentElement.style.getPropertyValue('--kn-sticky-header')).toBe('')
    } finally {
      Element.prototype.getBoundingClientRect = original
    }
  })

  it('los textos de una sección se editan en el borrador con su revisión y solo con sus campos', async () => {
    const user = userEvent.setup()
    api.updateSectionHeading.mockResolvedValue({})
    api.fetchAdminHome.mockResolvedValue(adminPageWith('disciplines', [], {
      revision: 7, headingFields: ['eyebrow', 'title'], heading: { eyebrow: null, title: 'Disciplinas', body: null },
    }))
    renderPage()
    const disciplines = await region('Disciplinas')
    expect(disciplines.getByText('Disciplinas', { selector: 'p' })).toBeInTheDocument()
    await user.click(disciplines.getByRole('button', { name: 'Editar textos de la sección' }))

    expect(disciplines.getByLabelText('Antetítulo (opcional)')).toHaveFocus()
    expect(disciplines.queryByLabelText('Texto (opcional)')).toBeNull()
    await user.type(disciplines.getByLabelText('Antetítulo (opcional)'), 'Servicios')
    await user.click(disciplines.getByRole('button', { name: 'Guardar borrador' }))

    expect(api.updateSectionHeading).toHaveBeenCalledWith('disciplines', { eyebrow: 'Servicios', title: 'Disciplinas', body: '' }, 7)
    expect(await screen.findByRole('status', { name: 'Avisos' })).toHaveTextContent('Borrador guardado')
  })

  it('el título del Hero explica que solo lo anuncian los lectores de pantalla', async () => {
    const user = userEvent.setup()
    api.fetchAdminHome.mockResolvedValue(adminPageWith('hero', [], { headingFields: ['title'] }))
    renderPage()
    const hero = await region('Hero')
    await user.click(hero.getByRole('button', { name: 'Editar textos de la sección' }))

    expect(hero.getByLabelText('Título (opcional)')).toHaveAccessibleDescription(/lectores de pantalla.*nombre del negocio/)
  })

  it('la previsualización usa el nombre del negocio como título del Hero sin cabecera', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Previsualizar' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Estudio de prueba' })).toBeInTheDocument()
  })
})
