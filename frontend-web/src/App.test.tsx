import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as authApi from '@/api/auth'
import { ApiError } from '@/api/http'

import * as settingsApi from '@/api/adminSettings'
import * as publicApi from '@/api/publicPage'
import * as siteApi from '@/api/site'
import { EMPTY_SITE } from '@/content/site'

import { App } from './App'

vi.mock('@/api/auth', () => ({
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))
vi.mock('@/api/publicPage', () => ({ fetchPublicHome: vi.fn() }))
vi.mock('@/api/site', () => ({ fetchPublicSite: vi.fn() }))
vi.mock('@/api/adminSettings', () => ({ fetchAdminSettings: vi.fn() }))

const api = vi.mocked(authApi)
const admin = { email: 'admin@example.com', fullName: 'Administración' }

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

async function submitLogin(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Correo electrónico'), email)
  await user.type(screen.getByLabelText('Contraseña'), password)
  await user.click(screen.getByRole('button', { name: /acceder al estudio/i }))
  return user
}

describe('portada pública', () => {
  beforeEach(() => {
    vi.mocked(siteApi.fetchPublicSite).mockResolvedValue({ ...EMPTY_SITE, businessName: 'Estudio de prueba' })
  })

  it('la portada es pública y no consulta la sesión', async () => {
    vi.mocked(publicApi.fetchPublicHome).mockResolvedValue({ pageKey: 'home', sections: [{ sectionKey: 'hero', heading: null, texts: [], items: [] }] })

    renderAt('/')

    expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument()
    expect(api.fetchCurrentUser).not.toHaveBeenCalled()
  })

  it('la cabecera muestra la identidad configurada enlazada a la portada', async () => {
    vi.mocked(publicApi.fetchPublicHome).mockResolvedValue({ pageKey: 'home', sections: [{ sectionKey: 'hero', heading: null, texts: [], items: [] }] })

    renderAt('/')

    const home = await screen.findByRole('link', { name: 'Estudio de prueba' })
    expect(home).toHaveAttribute('href', '/')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Estudio de prueba')
    expect(document.title).toBe('Estudio de prueba')
  })

  it('sin identidad configurada (o si no carga) usa el nombre provisional del producto', async () => {
    vi.mocked(siteApi.fetchPublicSite).mockRejectedValue(new ApiError(0, 'sin conexión'))
    vi.mocked(publicApi.fetchPublicHome).mockResolvedValue({ pageKey: 'home', sections: [{ sectionKey: 'hero', heading: null, texts: [], items: [] }] })

    renderAt('/')

    expect(await screen.findByRole('link', { name: 'Kinich' })).toHaveAttribute('href', '/')
  })

  it('si la portada no carga, ofrece reintentar', async () => {
    vi.mocked(publicApi.fetchPublicHome).mockRejectedValue(new ApiError(0, 'No se pudo conectar con el servidor.'))

    renderAt('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo conectar')
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })

  it('rutas desconocidas llevan a la portada', async () => {
    vi.mocked(publicApi.fetchPublicHome).mockReturnValue(new Promise(() => {}))

    renderAt('/no-existe')

    expect(await screen.findByRole('status')).toHaveTextContent('Cargando')
  })
})

describe('autenticación del panel', () => {
  beforeEach(() => {
    api.fetchCurrentUser.mockRejectedValue(new ApiError(401, 'Debes iniciar sesión para continuar.'))
    vi.mocked(siteApi.fetchPublicSite).mockResolvedValue({ ...EMPTY_SITE, businessName: 'Estudio de prueba', tagline: 'Lema de prueba' })
    vi.mocked(settingsApi.fetchAdminSettings).mockResolvedValue({ identityConfigured: true } as never)
  })

  it('el login muestra la identidad configurada, no la de un negocio fijo', async () => {
    const { container } = renderAt('/admin/login')

    expect(await screen.findByText('Lema de prueba')).toBeInTheDocument()
    const aside = container.querySelector('aside')!
    expect(aside).toHaveAttribute('aria-hidden', 'true')
    expect(aside).toHaveTextContent('Estudio de prueba')
  })

  it('el panel avisa si la identidad del negocio no está configurada', async () => {
    api.fetchCurrentUser.mockResolvedValue(admin)
    vi.mocked(settingsApi.fetchAdminSettings).mockResolvedValue({ identityConfigured: false } as never)

    renderAt('/admin')

    expect(await screen.findByText(/identidad del negocio todavía no está configurada/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Configurarla' })).toHaveAttribute('href', '/admin/configuracion')
    expect(screen.getByRole('link', { name: 'Configuración' })).toHaveAttribute('href', '/admin/configuracion')
  })

  it('muestra un estado de carga mientras verifica la sesión', () => {
    api.fetchCurrentUser.mockReturnValue(new Promise(() => {}))

    renderAt('/admin')

    expect(screen.getByRole('status')).toHaveTextContent('Verificando sesión…')
  })

  it('sin sesión, el panel redirige al login', async () => {
    renderAt('/admin')

    expect(await screen.findByRole('heading', { name: 'Acceso al Estudio' })).toBeInTheDocument()
  })

  it('con login correcto navega al panel', async () => {
    api.login.mockResolvedValue(admin)
    renderAt('/admin')

    await submitLogin('  admin@example.com ', 'clave-de-prueba-2026')

    expect(await screen.findByRole('heading', { name: 'Hola, Administración' })).toBeInTheDocument()
    expect(api.login).toHaveBeenCalledWith('admin@example.com', 'clave-de-prueba-2026')
  })

  it('con login incorrecto muestra el error del servidor y limpia la contraseña', async () => {
    api.login.mockRejectedValue(new ApiError(401, 'Correo o contraseña incorrectos.'))
    renderAt('/admin/login')

    await submitLogin('admin@example.com', 'incorrecta')

    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos.')
    expect(screen.getByLabelText('Contraseña')).toHaveValue('')
    expect(screen.getByLabelText('Correo electrónico')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('heading', { name: 'Acceso al Estudio' })).toBeInTheDocument()
  })

  it.each([
    [new ApiError(429, 'Demasiados intentos. Espera unos minutos antes de volver a intentarlo.'), 'Demasiados intentos'],
    [new ApiError(0, 'No se pudo conectar con el servidor. Revisa tu conexión.'), 'No se pudo conectar'],
    [new ApiError(400, 'La solicitud contiene datos inválidos.'), 'Revisa el correo y la contraseña.'],
    [new Error('fallo interno'), 'Ocurrió un error inesperado.'],
  ])('traduce errores de la API a mensajes seguros (%s)', async (error, expected) => {
    api.login.mockRejectedValue(error)
    renderAt('/admin/login')

    await submitLogin('admin@example.com', 'x')

    expect(await screen.findByRole('alert')).toHaveTextContent(expected)
  })

  it('no envía el formulario vacío', async () => {
    const user = userEvent.setup()
    renderAt('/admin/login')

    await user.click(await screen.findByRole('button', { name: /acceder al estudio/i }))

    expect(screen.getByRole('alert')).toHaveTextContent('Ingresa tu correo y tu contraseña.')
    expect(api.login).not.toHaveBeenCalled()
  })

  it('permite mostrar y ocultar la contraseña', async () => {
    const user = userEvent.setup()
    renderAt('/admin/login')
    const password = await screen.findByLabelText('Contraseña')

    await user.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(password).toHaveAttribute('type', 'text')

    await user.click(screen.getByRole('button', { name: 'Ocultar contraseña' }))
    expect(password).toHaveAttribute('type', 'password')
  })

  it('con sesión activa, el login redirige al panel', async () => {
    api.fetchCurrentUser.mockResolvedValue(admin)

    renderAt('/admin/login')

    expect(await screen.findByRole('heading', { name: 'Hola, Administración' })).toBeInTheDocument()
  })

  it('ignora destinos externos tras el login', async () => {
    api.login.mockResolvedValue(admin)
    renderAt('/admin/login?redirect=//evil.example')

    await submitLogin('admin@example.com', 'clave-de-prueba-2026')

    expect(await screen.findByRole('heading', { name: 'Hola, Administración' })).toBeInTheDocument()
  })

  it('cerrar sesión vuelve al login', async () => {
    api.fetchCurrentUser.mockResolvedValue(admin)
    api.logout.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderAt('/admin')

    await user.click(await screen.findByRole('button', { name: 'Cerrar sesión' }))

    expect(await screen.findByRole('heading', { name: 'Acceso al Estudio' })).toBeInTheDocument()
    expect(api.logout).toHaveBeenCalledOnce()
  })

  it('si cerrar sesión falla, lo informa y no finge que la sesión terminó', async () => {
    api.fetchCurrentUser.mockResolvedValue(admin)
    api.logout.mockRejectedValue(new ApiError(0, 'sin conexión'))
    const user = userEvent.setup()
    renderAt('/admin')

    await user.click(await screen.findByRole('button', { name: 'Cerrar sesión' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo cerrar la sesión.')
    expect(screen.getByRole('heading', { name: 'Hola, Administración' })).toBeInTheDocument()
  })
})
