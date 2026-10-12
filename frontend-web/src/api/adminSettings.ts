import { request } from './http'
import type { SiteContact, SiteLogo, SocialPlatform } from './site'

export interface AdminIdentity {
  businessName: string | null
  tagline: string | null
  logo: SiteLogo | null
  logoOnDark: SiteLogo | null
}

export interface AdminSocialLink {
  id: string
  platform: SocialPlatform
  label: string | null
  url: string
  visible: boolean
  position: number
  version: number
}

/** Configuración de la instalación. Se aplica al guardar; version es la de identidad + contacto. */
export interface AdminSettings {
  identityConfigured: boolean
  identity: AdminIdentity
  contact: SiteContact
  socialLinks: AdminSocialLink[]
  version: number
}

export interface IdentityValues {
  businessName: string
  tagline: string
}

/** Variantes explícitas del logotipo: principal (todos los temas) y, opcional, para fondo oscuro. */
export type LogoVariant = 'primary' | 'on-dark'

export interface ContactValues {
  email: string
  whatsapp: string
  phone: string
  location: string
  address: string
  openingHours: string
}

export interface SocialLinkValues {
  platform: SocialPlatform
  label: string
  url: string
  visible: boolean
}

const BASE = '/api/admin/settings'
const LINKS = `${BASE}/social-links`
const blankToNull = (value: string) => (value.trim() === '' ? null : value)

export function fetchAdminSettings(signal?: AbortSignal): Promise<AdminSettings> {
  return request<AdminSettings>(BASE, { signal })
}

export function updateIdentity(values: IdentityValues, version: number): Promise<AdminSettings> {
  return request<AdminSettings>(`${BASE}/identity`, {
    method: 'PUT',
    body: JSON.stringify({
      businessName: values.businessName,
      tagline: blankToNull(values.tagline),
      version,
    }),
  })
}

/** Sube el SVG de una variante; el backend lo sanitiza y reemplaza el anterior. */
export function uploadLogo(variant: LogoVariant, file: File, version: number): Promise<AdminSettings> {
  const body = new FormData()
  body.append('file', file)
  body.append('version', String(version))
  return request<AdminSettings>(`${BASE}/logos/${variant}`, { method: 'PUT', body })
}

export function removeLogo(variant: LogoVariant, version: number): Promise<AdminSettings> {
  return request<AdminSettings>(`${BASE}/logos/${variant}?version=${version}`, { method: 'DELETE' })
}

export function updateContact(values: ContactValues, version: number): Promise<AdminSettings> {
  return request<AdminSettings>(`${BASE}/contact`, {
    method: 'PUT',
    body: JSON.stringify({
      email: blankToNull(values.email),
      whatsapp: blankToNull(values.whatsapp),
      phone: blankToNull(values.phone),
      location: blankToNull(values.location),
      address: blankToNull(values.address),
      openingHours: blankToNull(values.openingHours),
      version,
    }),
  })
}

const toLinkBody = (values: SocialLinkValues) => ({
  platform: values.platform,
  label: blankToNull(values.label),
  url: values.url,
  visible: values.visible,
})

export function createSocialLink(values: SocialLinkValues): Promise<AdminSocialLink> {
  return request<AdminSocialLink>(LINKS, { method: 'POST', body: JSON.stringify(toLinkBody(values)) })
}

export function updateSocialLink(id: string, values: SocialLinkValues, version: number): Promise<AdminSocialLink> {
  return request<AdminSocialLink>(`${LINKS}/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ ...toLinkBody(values), version }),
  })
}

export function deleteSocialLink(id: string, version: number): Promise<void> {
  return request<void>(`${LINKS}/${id}?version=${version}`, { method: 'DELETE' })
}

export function reorderSocialLinks(items: { id: string; version: number }[]): Promise<void> {
  return request<void>(`${LINKS}/order`, { method: 'PUT', body: JSON.stringify({ items }) })
}
