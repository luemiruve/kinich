import type { PublicSite, SocialPlatform } from '@/api/site'

/** Nombre provisional de una instalación sin identidad configurada: el del producto, nunca el de un negocio. */
export const PRODUCT_NAME = 'Kinich'

/** Instalación vacía (o identidad no disponible): sin logotipo, contacto ni redes. */
export const EMPTY_SITE: PublicSite = {
  businessName: null,
  tagline: null,
  logo: null,
  logoOnDark: null,
  contact: { email: null, whatsapp: null, phone: null, location: null, address: null, openingHours: null },
  socialLinks: [],
}

export const displayName = (site: PublicSite) => site.businessName ?? PRODUCT_NAME

/** Nombres visibles de la lista cerrada; «Otra» usa el nombre que escribe el administrador. */
export const PLATFORM_LABELS: Record<SocialPlatform, string> = {
  INSTAGRAM: 'Instagram',
  FACEBOOK: 'Facebook',
  TIKTOK: 'TikTok',
  YOUTUBE: 'YouTube',
  VIMEO: 'Vimeo',
  PINTEREST: 'Pinterest',
  X: 'X',
  LINKEDIN: 'LinkedIn',
  OTHER: 'Otra',
}

export const SOCIAL_PLATFORMS = Object.keys(PLATFORM_LABELS) as SocialPlatform[]

export const socialLabel = (link: { platform: SocialPlatform; label: string | null }) =>
  link.label ?? PLATFORM_LABELS[link.platform]

/** Enlace de WhatsApp a partir de un número E.164 (wa.me exige solo dígitos). */
export const whatsappUrl = (e164: string) => `https://wa.me/${e164.replace(/\D/g, '')}`

/** Mismos máximos que las columnas de V8 (String.length = unidades UTF-16, igual que el backend). */
export const SETTINGS_LIMITS = {
  businessName: 120,
  tagline: 200,
  email: 254,
  phone: 32,
  location: 120,
  address: 300,
  openingHours: 500,
  linkLabel: 60,
  url: 2048,
  maxLinks: 20,
} as const
