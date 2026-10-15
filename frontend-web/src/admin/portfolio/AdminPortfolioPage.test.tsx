import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as pageApi from '@/api/adminPage'
import * as portfolioApi from '@/api/adminPortfolio'
import * as authApi from '@/api/auth'
import * as siteApi from '@/api/site'
import * as settingsApi from '@/api/adminSettings'
import { ApiError } from '@/api/http'
import { App } from '@/App'

import { portfolioPage, summary } from './fixtures'

vi.mock('@/api/auth', () => ({ fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('@/api/site', () => ({ fetchPublicSite: vi.fn() }))
vi.mock('@/api/adminSettings', () => ({ fetchAdminSettings: vi.fn() }))
vi.mock('@/api/adminPage', () => ({
  fetchAdminPortfolioPage: vi.fn(), updateSectionHeading: vi.fn(), publishSection: vi.fn(),
}))
vi.mock('@/api/adminPortfolio', () => ({
  fetchProjects: vi.fn(), fetchProject: vi.fn(), createProject: vi.fn(), updateProject: vi.fn(),
  setProjectVisibility: vi.fn(), deleteProject: vi.fn(), reorderProjects: vi.fn(), publishProject: vi.fn(),
  publishOrder: vi.fn(),
}))

const api = vi.mocked(portfolioApi)
const pages = vi.mocked(pageApi)

const list = (orderModified = false) => ({
  orderModified,
  projects: [
    summary('a', 1),
    summary('b', 2, { status: 'PUBLISHED' }),
    summary('c', 3, { status: 'PUBLISHED', modified: true }),
    summary('d', 4, { status: 'PUBLISHED', modified: true, hidden: true }),
    summary('e', 5, { status: 'HIDDEN', hidden: true }),
  ],
})

function renderPage(path = '/admin/portafolio') {
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
}

async function item(title: string) {
  return within(await screen.findByRole('listitem', { name: title }))
}

describe('/admin/portafolio', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(siteApi.fetchPublicSite).mockResolvedValue(null as never)
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue({ email: 'admin@example.com', fullName: 'Administración' })
    vi.mocked(settingsApi.fetchAdminSettings).mockResolvedValue({ identityConfigured: true } as never)
    api.fetchProjects.mockResolvedValue(list())
    pages.fetchAdminPortfolioPage.mockResolvedValue(portfolioPage())
  })

  it('el panel enlaza con Portafolio', async () => {
    renderPage('/admin')
    expect(await screen.findByRole('link', { name: 'Editar Portafolio' })).toHaveAttribute('href', '/admin/portafolio')
  })

  it('muestra el estado de cada proyecto', async () => {
    renderPage()
    expect((await item('Proyecto a')).getByText('Borrador')).toBeInTheDocument()
    expect((await item('Proyecto b')).getByText('Publicado')).toBeInTheDocument()
    expect((await item('Proyecto c')).getByText('Publicado con cambios')).toBeInTheDocument()
    const pending = await item('Proyecto d')
    expect(pending.getByText('Retirada pendiente')).toBeInTheDocument()
    expect(pending.getByText(/sigue visible en el sitio hasta que publiques/i)).toBeInTheDocument()
    expect((await item('Proyecto e')).getByText('Oculto')).toBeInTheDocument()
  })

  it('bajar con teclado guarda el orden y mantiene el foco en el proyecto movido', async () => {
    const user = userEvent.setup()
    api.reorderProjects.mockResolvedValue(list())
    api.fetchProjects.mockResolvedValueOnce(list()).mockResolvedValue({
      orderModified: false,
      projects: [summary('b', 1, { version: 9 }), summary('a', 2, { version: 8 }), ...list().projects.slice(2)],
    })
    renderPage()
    const down = (await item('Proyecto a')).getByRole('button', { name: 'Bajar «Proyecto a»' })
    down.focus()
    await user.keyboard('{Enter}')
    expect(api.reorderProjects).toHaveBeenCalledWith([
      { id: 'b', version: 2 }, { id: 'a', version: 1 }, { id: 'c', version: 3 }, { id: 'd', version: 4 }, { id: 'e', version: 5 },
    ])
    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Bajar «Proyecto a»' })))
  })

  it('«Publicar orden» solo aparece con el orden modificado y publica el orden guardado', async () => {
    const { unmount } = renderPage()
    await item('Proyecto a')
    expect(screen.queryByRole('button', { name: 'Publicar orden' })).toBeNull()
    unmount()

    const user = userEvent.setup()
    api.fetchProjects.mockResolvedValue(list(true))
    api.publishOrder.mockResolvedValue(list())
    renderPage()
    expect(await screen.findByText('Orden con cambios sin publicar')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Publicar orden' }))
    expect(api.publishOrder).toHaveBeenCalledWith(list().projects.map(({ id, version }) => ({ id, version })))
  })

  it('eliminar pide confirmación y avisa de que es inmediato', async () => {
    const user = userEvent.setup()
    api.deleteProject.mockResolvedValue(undefined)
    renderPage()
    await user.click((await item('Proyecto b')).getByRole('button', { name: 'Eliminar «Proyecto b»' }))
    const dialog = screen.getByRole('alertdialog', { name: '¿Eliminar «Proyecto b»?' })
    expect(within(dialog).getByText(/de inmediato/)).toBeInTheDocument()
    expect(api.deleteProject).not.toHaveBeenCalled()
    await user.click(within(dialog).getByRole('button', { name: 'Eliminar definitivamente' }))
    expect(api.deleteProject).toHaveBeenCalledWith('b', 2)
    await waitFor(() => expect(api.fetchProjects).toHaveBeenCalledTimes(2))
  })

  it('retirar marca el proyecto como oculto y explica que se aplica al publicar', async () => {
    const user = userEvent.setup()
    api.setProjectVisibility.mockResolvedValue({} as never)
    renderPage()
    await user.click((await item('Proyecto b')).getByRole('button', { name: 'Retirar «Proyecto b»' }))
    expect(api.setProjectVisibility).toHaveBeenCalledWith('b', true, 2)
    expect(await screen.findByText(/Se retirará del sitio al publicar el proyecto/)).toBeInTheDocument()
  })

  it.each([
    ['Proyecto d', 'Se cancela la retirada; sigue visible en el sitio.'],
    ['Proyecto e', 'Se mostrará en el sitio al publicar el proyecto'],
  ])('mostrar «%s» explica su efecto', async (title, message) => {
    const user = userEvent.setup()
    api.setProjectVisibility.mockResolvedValue({} as never)
    renderPage()
    await user.click((await item(title)).getByRole('button', { name: `Mostrar «${title}»` }))
    expect(await screen.findByText(message)).toBeInTheDocument()
  })

  it('un 409 recarga la lista, avisa y muestra los datos del servidor', async () => {
    const user = userEvent.setup()
    api.publishProject.mockRejectedValue(new ApiError(409, 'El contenido cambió.', {}, 'VERSION_CONFLICT'))
    renderPage()
    const publish = (await item('Proyecto a')).getByRole('button', { name: 'Publicar «Proyecto a»' })
    api.fetchProjects.mockResolvedValue({ orderModified: false, projects: [summary('a', 1, { title: 'Título del servidor' })] })
    await user.click(publish)
    expect(await screen.findByText(/El contenido cambió\..*recargado/)).toBeInTheDocument()
    expect(api.fetchProjects).toHaveBeenCalledTimes(2)
    expect(await screen.findByRole('listitem', { name: 'Título del servidor' })).toBeInTheDocument()
    expect(screen.queryByRole('listitem', { name: 'Proyecto b' })).not.toBeInTheDocument()
  })

  it('un 401 cierra la sesión', async () => {
    api.fetchProjects.mockRejectedValue(new ApiError(401, 'Debes iniciar sesión.'))
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Acceso al Estudio' })).toBeInTheDocument()
  })

  it('«Nuevo proyecto» se deshabilita con 200 proyectos', async () => {
    api.fetchProjects.mockResolvedValue({
      orderModified: false,
      projects: Array.from({ length: 200 }, (_, i) => summary(`p${i}`, i + 1)),
    })
    renderPage()
    expect(await screen.findByRole('button', { name: 'Nuevo proyecto' })).toBeDisabled()
    expect(screen.getByText(/máximo de 200 proyectos/)).toBeInTheDocument()
  })

  it('los textos de la página se editan y publican en su sección', async () => {
    const user = userEvent.setup()
    pages.publishSection.mockResolvedValue(undefined)
    pages.updateSectionHeading.mockResolvedValue(undefined)
    renderPage()
    const header = within(await screen.findByRole('region', { name: 'Cabecera de la página' }))
    expect(header.getByText('Historias')).toBeInTheDocument()
    await user.click(header.getByRole('button', { name: 'Editar textos de la sección' }))
    await user.clear(header.getByLabelText(/^Título/))
    await user.type(header.getByLabelText(/^Título/), 'Portafolio')
    await user.click(header.getByRole('button', { name: 'Guardar borrador' }))
    expect(pages.updateSectionHeading).toHaveBeenCalledWith('page-header',
      { eyebrow: '', title: 'Portafolio', body: '' }, 2, 'portfolio')
    const closing = within(screen.getByRole('region', { name: 'Cierre' }))
    await user.click(closing.getByRole('button', { name: 'Publicar sección' }))
    expect(pages.publishSection).toHaveBeenCalledWith('closing-cta', 4, 'portfolio')
  })
})
