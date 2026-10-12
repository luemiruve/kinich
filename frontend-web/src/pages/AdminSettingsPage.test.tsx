import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as settingsApi from '@/api/adminSettings'
import type { AdminSettings, AdminSocialLink } from '@/api/adminSettings'
import * as authApi from '@/api/auth'
import { ApiError } from '@/api/http'
import * as siteApi from '@/api/site'
import { App } from '@/App'

vi.mock('@/api/auth', () => ({ fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('@/api/site', () => ({ fetchPublicSite: vi.fn() }))
vi.mock('@/api/adminSettings', () => ({
  fetchAdminSettings: vi.fn(), updateIdentity: vi.fn(), updateContact: vi.fn(), createSocialLink: vi.fn(),
  updateSocialLink: vi.fn(), deleteSocialLink: vi.fn(), reorderSocialLinks: vi.fn(), uploadLogo: vi.fn(),
  removeLogo: vi.fn(),
}))

const api = vi.mocked(settingsApi)

const link = (id: string, position: number, extra: Partial<AdminSocialLink> = {}): AdminSocialLink => ({
  id, platform: 'INSTAGRAM', label: null, url: `https://www.instagram.com/${id}`, visible: true, position, version: position,
  ...extra,
})

const empty: AdminSettings = {
  identityConfigured: false,
  identity: { businessName: null, tagline: null, logo: null, logoOnDark: null },
  contact: { email: null, whatsapp: null, phone: null, location: null, address: null, openingHours: null },
  socialLinks: [],
  version: 3,
}

const configured: AdminSettings = {
  ...empty, identityConfigured: true, identity: { ...empty.identity, businessName: 'Estudio de prueba' },
  socialLinks: [link('a', 1), link('b', 2, { platform: 'OTHER', label: 'Portafolio externo' })],
}

const primary = { url: '/api/public/brand-assets/principal', width: 240, height: 60 }
const onDark = { url: '/api/public/brand-assets/oscuro', width: 240, height: 60 }
const svgFile = () => new File(['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 60"/>'], 'logo.svg', { type: 'image/svg+xml' })

function renderPage() {
  return render(<MemoryRouter initialEntries={['/admin/configuracion']}><App /></MemoryRouter>)
}

async function area(name: string) {
  return within(await screen.findByRole('region', { name }))
}

describe('/admin/configuracion', () => {
  beforeEach(() => {
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue({ email: 'admin@example.com', fullName: 'Administración' })
    api.fetchAdminSettings.mockResolvedValue(empty)
    // El login (sin sesión) consulta la identidad pública; aquí no importa su contenido.
    vi.mocked(siteApi.fetchPublicSite).mockReturnValue(new Promise(() => {}))
  })

  it('exige sesión', async () => {
    vi.mocked(authApi.fetchCurrentUser).mockRejectedValue(new ApiError(401, 'Debes iniciar sesión.'))
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Acceso al Estudio' })).toBeInTheDocument()
  })

  it('una instalación nueva indica que falta la identidad y separa las tres áreas', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'Configuración' })).toBeInTheDocument()
    expect(screen.getByText(/todavía no está configurada/)).toBeInTheDocument()
    for (const name of ['Identidad', 'Contacto', 'Redes sociales']) {
      expect(screen.getByRole('region', { name })).toBeInTheDocument()
    }
    expect(screen.getByText('Todavía no hay redes sociales.')).toBeInTheDocument()
  })

  it('guardar la identidad envía nombre y lema con la versión y se aplica sin publicar', async () => {
    const user = userEvent.setup()
    api.updateIdentity.mockResolvedValue({ ...configured, version: 4 })
    renderPage()
    const identity = await area('Identidad')
    await user.type(identity.getByLabelText('Nombre del negocio o estudio'), 'Estudio de prueba')
    await user.type(identity.getByLabelText('Lema (opcional)'), 'Fotografía y video')
    await user.click(identity.getByRole('button', { name: 'Guardar identidad' }))

    expect(api.updateIdentity).toHaveBeenCalledWith({ businessName: 'Estudio de prueba', tagline: 'Fotografía y video' }, 3)
    expect(await screen.findByRole('status', { name: 'Avisos' })).toHaveTextContent('Identidad guardada')
    expect(screen.queryByText(/todavía no está configurada/)).toBeNull()
  })

  it('los logotipos se suben como SVG por variante, sin la biblioteca de medios, y cada subida usa la versión vigente', async () => {
    const user = userEvent.setup()
    api.uploadLogo
      .mockResolvedValueOnce({ ...empty, identity: { ...empty.identity, logo: primary }, version: 4 })
      .mockResolvedValueOnce({ ...empty, identity: { ...empty.identity, logo: primary, logoOnDark: onDark }, version: 5 })
    renderPage()
    const identity = await area('Identidad')
    const main = within(identity.getByRole('group', { name: 'Logotipo principal' }))
    const dark = within(identity.getByRole('group', { name: 'Logotipo para fondo oscuro (opcional)' }))
    expect(identity.queryByRole('button', { name: 'Elegir logotipo' })).toBeNull()
    expect(main.getByLabelText('Subir SVG')).toHaveAttribute('accept', '.svg,image/svg+xml')
    // La variante oscura no puede existir sin principal.
    expect(dark.getByLabelText('Subir SVG')).toBeDisabled()

    const first = svgFile()
    await user.upload(main.getByLabelText('Subir SVG'), first)
    expect(api.uploadLogo).toHaveBeenCalledWith('primary', first, 3)
    expect(await main.findByRole('img', { name: 'Vista previa: Kinich' })).toHaveAttribute('src', primary.url)
    expect(screen.getByRole('status', { name: 'Avisos' })).toHaveTextContent('Logotipo actualizado')

    const second = svgFile()
    await user.upload(dark.getByLabelText('Subir SVG'), second)
    expect(api.uploadLogo).toHaveBeenLastCalledWith('on-dark', second, 4)
    expect(await dark.findByRole('img', { name: 'Vista previa: Kinich' })).toHaveAttribute('src', onDark.url)
  })

  it('quitar una variante la retira con la versión vigente y un SVG rechazado muestra el motivo en su variante', async () => {
    const user = userEvent.setup()
    api.fetchAdminSettings.mockResolvedValue({ ...empty, identity: { ...empty.identity, logo: primary, logoOnDark: onDark } })
    api.removeLogo.mockResolvedValue({ ...empty, identity: { ...empty.identity, logo: primary }, version: 4 })
    api.uploadLogo.mockRejectedValue(new ApiError(400, 'La solicitud contiene datos inválidos.', { file: 'elemento no permitido: script' }))
    renderPage()
    const identity = await area('Identidad')
    const main = within(identity.getByRole('group', { name: 'Logotipo principal' }))
    const dark = within(identity.getByRole('group', { name: 'Logotipo para fondo oscuro (opcional)' }))

    await user.click(dark.getByRole('button', { name: 'Quitar' }))
    expect(api.removeLogo).toHaveBeenCalledWith('on-dark', 3)
    await waitFor(() => expect(dark.queryByRole('img')).toBeNull())

    await user.upload(main.getByLabelText('Subir SVG'), svgFile())
    expect(await main.findByText('elemento no permitido: script')).toBeInTheDocument()
    expect(dark.queryByText('elemento no permitido: script')).toBeNull()
  })

  it('los errores 400 se asocian a su campo', async () => {
    const user = userEvent.setup()
    api.updateIdentity.mockRejectedValue(new ApiError(400, 'La solicitud contiene datos inválidos.', { businessName: 'es obligatorio' }))
    renderPage()
    const identity = await area('Identidad')
    await user.type(identity.getByLabelText('Nombre del negocio o estudio'), 'x')
    await user.click(identity.getByRole('button', { name: 'Guardar identidad' }))

    expect(await identity.findByText('es obligatorio')).toBeInTheDocument()
    expect(identity.getByLabelText('Nombre del negocio o estudio')).toHaveAccessibleDescription('es obligatorio')
  })

  it('un conflicto de versión avisa, recarga y repone los datos del servidor', async () => {
    const user = userEvent.setup()
    api.updateContact.mockRejectedValue(new ApiError(409, 'El contenido cambió desde que lo cargaste.', {}, 'VERSION_CONFLICT'))
    renderPage()
    const contact = await area('Contacto')
    await user.type(contact.getByLabelText('Correo electrónico'), 'contacto@example.com')
    api.fetchAdminSettings.mockResolvedValue({ ...empty, contact: { ...empty.contact, email: 'otro@example.com' }, version: 5 })
    await user.click(contact.getByRole('button', { name: 'Guardar contacto' }))

    expect(await screen.findByRole('status', { name: 'Avisos' })).toHaveTextContent('El contenido cambió')
    await waitFor(() => expect((screen.getByLabelText('Correo electrónico') as HTMLInputElement).value).toBe('otro@example.com'))
  })

  it('el contacto se guarda con todos sus campos', async () => {
    const user = userEvent.setup()
    api.updateContact.mockResolvedValue(empty)
    renderPage()
    const contact = await area('Contacto')
    await user.type(contact.getByLabelText('WhatsApp'), '+1 555 555 0100')
    await user.type(contact.getByLabelText('Dirección'), 'Calle 1{Enter}Interior 2')
    await user.click(contact.getByRole('button', { name: 'Guardar contacto' }))

    expect(api.updateContact).toHaveBeenCalledWith({
      email: '', whatsapp: '+1 555 555 0100', phone: '', location: '', address: 'Calle 1\nInterior 2', openingHours: '',
    }, 3)
    expect(contact.getByLabelText('WhatsApp')).toHaveAccessibleDescription(/Formato internacional/)
  })

  it('una red social «Otra» pide su nombre y se crea visible', async () => {
    const user = userEvent.setup()
    api.createSocialLink.mockResolvedValue(link('c', 1))
    renderPage()
    const social = await area('Redes sociales')
    await user.click(social.getByRole('button', { name: 'Nueva red social' }))
    expect(social.getByLabelText('Plataforma')).toHaveFocus()
    await user.selectOptions(social.getByLabelText('Plataforma'), 'OTHER')
    expect(social.getByLabelText('Nombre de la plataforma')).toBeRequired()
    await user.type(social.getByLabelText('Nombre de la plataforma'), 'Portafolio externo')
    await user.clear(social.getByLabelText('Enlace'))
    await user.type(social.getByLabelText('Enlace'), 'https://portfolio.example.com')
    await user.click(social.getByRole('button', { name: 'Guardar' }))

    expect(api.createSocialLink).toHaveBeenCalledWith({
      platform: 'OTHER', label: 'Portafolio externo', url: 'https://portfolio.example.com', visible: true,
    })
    await waitFor(() => expect(api.fetchAdminSettings).toHaveBeenCalledTimes(2))
  })

  it('reordenar envía el orden completo con las versiones y eliminar pide confirmación', async () => {
    const user = userEvent.setup()
    api.fetchAdminSettings.mockResolvedValue(configured)
    api.reorderSocialLinks.mockResolvedValue(undefined)
    api.deleteSocialLink.mockResolvedValue(undefined)
    renderPage()
    const social = await area('Redes sociales')
    await user.click(social.getByRole('button', { name: 'Bajar Instagram' }))
    expect(api.reorderSocialLinks).toHaveBeenCalledWith([{ id: 'b', version: 2 }, { id: 'a', version: 1 }])

    await user.click(social.getAllByRole('button', { name: 'Eliminar' })[1])
    const dialog = await screen.findByRole('alertdialog', { name: '¿Eliminar «Portafolio externo»?' })
    expect(dialog).toHaveTextContent('Se quita del sitio en cuanto confirmes.')
    await user.click(within(dialog).getByRole('button', { name: 'Eliminar definitivamente' }))
    expect(api.deleteSocialLink).toHaveBeenCalledWith('b', 2)
  })
})
