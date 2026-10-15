import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as mediaApi from '@/api/adminMedia'
import * as portfolioApi from '@/api/adminPortfolio'
import * as pageApi from '@/api/adminPage'
import * as authApi from '@/api/auth'
import * as siteApi from '@/api/site'
import { ApiError } from '@/api/http'
import { App } from '@/App'

import { media, portfolioPage, project } from './fixtures'

vi.mock('@/api/auth', () => ({ fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('@/api/site', () => ({ fetchPublicSite: vi.fn() }))
vi.mock('@/api/adminPage', () => ({
  fetchAdminPortfolioPage: vi.fn(), updateSectionHeading: vi.fn(), publishSection: vi.fn(),
}))
vi.mock('@/api/adminMedia', () => ({ uploadMedia: vi.fn(), fetchMedia: vi.fn(), fetchCategories: vi.fn(), createCategory: vi.fn() }))
vi.mock('@/api/adminPortfolio', () => ({
  fetchProjects: vi.fn(), fetchProject: vi.fn(), createProject: vi.fn(), updateProject: vi.fn(),
  setProjectVisibility: vi.fn(), deleteProject: vi.fn(), reorderProjects: vi.fn(), publishProject: vi.fn(),
  publishOrder: vi.fn(),
}))

const api = vi.mocked(portfolioApi)

/** Router de datos (como en main.tsx): `useBlocker` lo necesita. Devuelve el router para comprobar la ubicación. */
function renderPage(path: string, previous: string[] = []) {
  const router = createMemoryRouter([{ path: '*', element: <App /> }], {
    initialEntries: [...previous, path], initialIndex: previous.length,
  })
  render(<RouterProvider router={router} />)
  return router
}

async function chooseMedia(user: ReturnType<typeof userEvent.setup>, buttonName: string) {
  await user.click(screen.getByRole('button', { name: buttonName }))
  await user.click(await screen.findByRole('button', { name: /Imagen 1600 × 1067/ }))
}

describe('editor de proyecto', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(siteApi.fetchPublicSite).mockResolvedValue(null as never)
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue({ email: 'admin@example.com', fullName: 'Administración' })
    vi.mocked(mediaApi.fetchMedia).mockResolvedValue([media('m9')])
    vi.mocked(mediaApi.fetchCategories).mockResolvedValue([{ id: 'c1', label: 'Bodas' }])
    api.fetchProject.mockResolvedValue(project())
  })

  it('el slug sigue al título hasta que se edita a mano', async () => {
    const user = userEvent.setup()
    renderPage('/admin/portafolio/nuevo')
    const title = await screen.findByLabelText(/^Título/)
    const slug = screen.getByLabelText(/^Slug/)
    await user.type(title, 'Elena & Carlos • Boda')
    expect(slug).toHaveValue('elena-carlos-boda')
    await user.clear(slug)
    await user.type(slug, 'mi-boda')
    await user.type(title, ' en Mérida')
    expect(slug).toHaveValue('mi-boda')
  })

  it('crear envía el agregado con portada y galería', async () => {
    const user = userEvent.setup()
    api.createProject.mockResolvedValue(project({ id: 'nuevo-id' }))
    renderPage('/admin/portafolio/nuevo')
    await user.type(await screen.findByLabelText(/^Título/), 'Boda')
    await chooseMedia(user, 'Elegir portada')
    await user.type(screen.getByLabelText(/Texto alternativo de la portada/), 'Portada')
    await chooseMedia(user, 'Añadir imagen')
    expect(screen.getByText('1 de 60')).toBeInTheDocument()
    await user.type(screen.getByLabelText(/Texto alternativo de la imagen 1/), 'Uno')
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    expect(api.createProject).toHaveBeenCalledWith({
      slug: 'boda', title: 'Boda', eyebrow: '', categoryId: null, description: '',
      cover: { mediaId: 'm9', alt: 'Portada', crop: null },
      images: [{ mediaId: 'm9', alt: 'Uno', caption: '' }],
    })
    await waitFor(() => expect(api.fetchProject).toHaveBeenCalledWith('nuevo-id', expect.anything()))
  })

  it('los errores por campo se muestran junto a su campo (incluido el recorte de la portada)', async () => {
    const user = userEvent.setup()
    api.updateProject.mockRejectedValue(new ApiError(400, 'Revisa los campos.', {
      slug: 'ya está en uso', 'images[1].alt': 'es obligatorio', 'images[0].caption': 'demasiado largo',
      'cover.crop.x': 'es inválido',
    }, 'VALIDATION_FAILED'))
    renderPage('/admin/portafolio/p1')
    await screen.findByDisplayValue('Elena y Carlos')
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    expect(await screen.findByText('ya está en uso')).toBeInTheDocument()
    expect(screen.getByLabelText(/^Slug/)).toHaveAccessibleDescription(/ya está en uso/)
    expect(screen.getByLabelText(/Texto alternativo de la imagen 2/)).toHaveAccessibleDescription('es obligatorio')
    expect(screen.getByLabelText(/Pie de foto de la imagen 1/)).toHaveAccessibleDescription('demasiado largo')
    const cover = within(screen.getByRole('group', { name: 'Portada' }))
    expect(cover.getByText('es inválido')).toBeInTheDocument()
  })

  it.each(['crop', 'cover.crop'])('el error «%s» se muestra en la portada y describe su botón', async (key) => {
    const user = userEvent.setup()
    api.updateProject.mockRejectedValue(new ApiError(400, 'Revisa los campos.', { [key]: 'recorte inválido' }, 'VALIDATION_FAILED'))
    renderPage('/admin/portafolio/p1')
    await screen.findByDisplayValue('Elena y Carlos')
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    const cover = within(screen.getByRole('group', { name: 'Portada' }))
    expect(await cover.findByText('recorte inválido')).toBeInTheDocument()
    expect(cover.getByRole('button', { name: 'Cambiar portada' })).toHaveAccessibleDescription('recorte inválido')
  })

  it('los errores de la galería y de un medio se asocian a sus controles', async () => {
    const user = userEvent.setup()
    api.updateProject.mockRejectedValue(new ApiError(400, 'Revisa los campos.', {
      images: 'faltan imágenes', 'images[0].mediaId': 'medio no disponible',
    }, 'VALIDATION_FAILED'))
    renderPage('/admin/portafolio/p1')
    await screen.findByDisplayValue('Elena y Carlos')
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    expect(await screen.findByText('faltan imágenes')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Añadir imagen' })).toHaveAccessibleDescription(/faltan imágenes/)
    expect(screen.getByLabelText(/Texto alternativo de la imagen 1/)).toHaveAccessibleDescription('medio no disponible')
  })

  it('elegir otra foto de portada vacía su texto alternativo', async () => {
    const user = userEvent.setup()
    renderPage('/admin/portafolio/p1')
    const alt = await screen.findByLabelText(/Texto alternativo de la portada/)
    expect(alt).toHaveValue('Portada de la boda')
    await chooseMedia(user, 'Cambiar portada')
    expect(screen.getByLabelText(/Texto alternativo de la portada/)).toHaveValue('')
  })

  it('un 409 recarga y repone los datos del servidor', async () => {
    const user = userEvent.setup()
    api.updateProject.mockRejectedValue(new ApiError(409, 'El contenido cambió.', {}, 'VERSION_CONFLICT'))
    api.fetchProject.mockResolvedValueOnce(project()).mockResolvedValue(project({ title: 'Título del servidor', version: 7 }))
    renderPage('/admin/portafolio/p1')
    const title = await screen.findByDisplayValue('Elena y Carlos')
    await user.clear(title)
    await user.type(title, 'Mi cambio')
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    expect(await screen.findByDisplayValue('Título del servidor')).toBeInTheDocument()
    expect(screen.getByText(/El contenido cambió\..*recargado/)).toBeInTheDocument()
  })

  it('MAX_ITEMS_REACHED se muestra como mensaje', async () => {
    const user = userEvent.setup()
    api.createProject.mockRejectedValue(new ApiError(409, 'Se alcanzó el máximo.', {}, 'MAX_ITEMS_REACHED'))
    renderPage('/admin/portafolio/nuevo')
    await user.type(await screen.findByLabelText(/^Título/), 'Boda')
    await chooseMedia(user, 'Elegir portada')
    await user.type(screen.getByLabelText(/Texto alternativo de la portada/), 'Portada')
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    expect(await screen.findByText(/máximo de 200 proyectos/)).toBeInTheDocument()
  })

  it('MAX_ITEMS_REACHED al guardar un proyecto existente habla del máximo de imágenes', async () => {
    const user = userEvent.setup()
    api.updateProject.mockRejectedValue(new ApiError(409, 'Se alcanzó el máximo.', {}, 'MAX_ITEMS_REACHED'))
    renderPage('/admin/portafolio/p1')
    await screen.findByDisplayValue('Elena y Carlos')
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    expect(await screen.findByText(/como máximo 60 imágenes/)).toBeInTheDocument()
  })

  it('mover una imagen de la galería mantiene el foco en ella', async () => {
    const user = userEvent.setup()
    renderPage('/admin/portafolio/p1')
    const down = await screen.findByRole('button', { name: 'Bajar imagen 1' })
    down.focus()
    await user.keyboard('{Enter}')
    expect(screen.getByLabelText(/Texto alternativo de la imagen 2/)).toHaveValue('Primer baile')
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Subir imagen 2' }))
  })

  it('la vista previa usa los componentes públicos con los datos del borrador', async () => {
    const user = userEvent.setup()
    renderPage('/admin/portafolio/p1')
    await screen.findByDisplayValue('Elena y Carlos')
    await user.click(screen.getByRole('button', { name: 'Previsualizar' }))
    expect(screen.getByRole('link', { name: 'Elena y Carlos' })).toHaveAttribute('href', '/portafolio/elena-carlos')
    expect(screen.getByRole('heading', { level: 1, name: 'Elena y Carlos' })).toBeInTheDocument()
    expect(screen.getByText('El primer baile')).toBeInTheDocument()
  })

  it('publicar usa la versión guardada y se bloquea con cambios sin guardar', async () => {
    const user = userEvent.setup()
    api.publishProject.mockResolvedValue(project({ status: 'PUBLISHED', version: 4 }))
    renderPage('/admin/portafolio/p1')
    const title = await screen.findByDisplayValue('Elena y Carlos')
    await user.type(title, '!')
    expect(screen.getByRole('button', { name: 'Publicar' })).toBeDisabled()
    await user.type(title, '{Backspace}')
    await user.click(screen.getByRole('button', { name: 'Publicar' }))
    expect(api.publishProject).toHaveBeenCalledWith('p1', 3)
  })
})

describe('aviso de cambios sin guardar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(siteApi.fetchPublicSite).mockResolvedValue(null as never)
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue({ email: 'admin@example.com', fullName: 'Administración' })
    vi.mocked(mediaApi.fetchCategories).mockResolvedValue([{ id: 'c1', label: 'Bodas' }])
    vi.mocked(pageApi.fetchAdminPortfolioPage).mockResolvedValue(portfolioPage())
    api.fetchProjects.mockResolvedValue({ projects: [], orderModified: false })
    api.fetchProject.mockResolvedValue(project())
  })

  async function editTitle(user: ReturnType<typeof userEvent.setup>) {
    const title = await screen.findByDisplayValue('Elena y Carlos')
    await user.clear(title)
    await user.type(title, 'Mi cambio')
  }

  const unloadPrevented = () => {
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    return event.defaultPrevented
  }

  it('sin cambios se sale sin aviso y no se registra beforeunload', async () => {
    const user = userEvent.setup()
    const router = renderPage('/admin/portafolio/p1')
    await screen.findByDisplayValue('Elena y Carlos')
    expect(unloadPrevented()).toBe(false)
    await user.click(screen.getByRole('link', { name: '← Portafolio' }))
    expect(router.state.location.pathname).toBe('/admin/portafolio')
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('con cambios avisa antes de salir con un alertdialog accesible', async () => {
    const user = userEvent.setup()
    const router = renderPage('/admin/portafolio/p1')
    await editTitle(user)
    expect(unloadPrevented()).toBe(true)
    await user.click(screen.getByRole('link', { name: '← Portafolio' }))
    const dialog = await screen.findByRole('alertdialog', { name: '¿Salir sin guardar?' })
    expect(dialog).toHaveAccessibleDescription(/cambios sin guardar se perderán/)
    expect(within(dialog).getByRole('button', { name: 'Seguir editando' })).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Salir sin guardar' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/portafolio/p1')
  })

  it('cancelar mantiene el editor con los datos y devuelve el foco', async () => {
    const user = userEvent.setup()
    const router = renderPage('/admin/portafolio/p1')
    await editTitle(user)
    const back = screen.getByRole('link', { name: '← Portafolio' })
    await user.click(back)
    await user.click(await screen.findByRole('button', { name: 'Seguir editando' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/portafolio/p1')
    expect(screen.getByLabelText(/^Título/)).toHaveValue('Mi cambio')
    expect(document.activeElement).toBe(back)
    expect(unloadPrevented()).toBe(true)
  })

  it('Escape equivale a cancelar', async () => {
    const user = userEvent.setup()
    const router = renderPage('/admin/portafolio/p1')
    await editTitle(user)
    await user.click(screen.getByRole('link', { name: '← Portafolio' }))
    // jsdom no dispara `cancel` al pulsar Escape en un <dialog>: se emite como lo haría el navegador.
    fireEvent(await screen.findByRole('alertdialog'), new Event('cancel', { cancelable: true }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/portafolio/p1')
    expect(screen.getByLabelText(/^Título/)).toHaveValue('Mi cambio')
  })

  it('confirmar sale sin guardar', async () => {
    const user = userEvent.setup()
    const router = renderPage('/admin/portafolio/p1')
    await editTitle(user)
    await user.click(screen.getByRole('link', { name: '← Portafolio' }))
    await user.click(await screen.findByRole('button', { name: 'Salir sin guardar' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/portafolio'))
    expect(api.updateProject).not.toHaveBeenCalled()
    expect(unloadPrevented()).toBe(false)
  })

  it('el botón Atrás del navegador también avisa', async () => {
    const user = userEvent.setup()
    const router = renderPage('/admin/portafolio/p1', ['/admin/portafolio'])
    await editTitle(user)
    await router.navigate(-1)
    expect(await screen.findByRole('alertdialog')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/portafolio/p1')
  })

  it('tras guardar correctamente ya no avisa', async () => {
    const user = userEvent.setup()
    api.updateProject.mockResolvedValue(project({ title: 'Mi cambio', version: 4 }))
    const router = renderPage('/admin/portafolio/p1')
    await editTitle(user)
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    expect(await screen.findByText('Borrador guardado')).toBeInTheDocument()
    expect(unloadPrevented()).toBe(false)
    await user.click(screen.getByRole('link', { name: '← Portafolio' }))
    expect(router.state.location.pathname).toBe('/admin/portafolio')
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('crear un proyecto nuevo pasa a su página sin aviso', async () => {
    const user = userEvent.setup()
    api.createProject.mockResolvedValue(project({ id: 'nuevo-id' }))
    vi.mocked(mediaApi.fetchMedia).mockResolvedValue([media('m9')])
    const router = renderPage('/admin/portafolio/nuevo')
    await user.type(await screen.findByLabelText(/^Título/), 'Boda')
    await chooseMedia(user, 'Elegir portada')
    await user.type(screen.getByLabelText(/Texto alternativo de la portada/), 'Portada')
    await user.click(screen.getByRole('button', { name: 'Guardar borrador' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/portafolio/nuevo-id'))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })
})
