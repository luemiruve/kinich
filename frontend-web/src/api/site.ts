import { ApiError, toApiError } from './http'

export type SocialPlatform =
  | 'INSTAGRAM' | 'FACEBOOK' | 'TIKTOK' | 'YOUTUBE' | 'VIMEO' | 'PINTEREST' | 'X' | 'LINKEDIN' | 'OTHER'

/**
 * Logotipo SVG (recurso de marca): URL pública opaca y proporción de su viewBox, para fijar ancho y alto en <img>
 * sin saltos de layout.
 */
export interface SiteLogo {
  url: string
  width: number
  height: number
}

export interface SiteContact {
  email: string | null
  /** E.164 (+ y dígitos). */
  whatsapp: string | null
  phone: string | null
  location: string | null
  address: string | null
  openingHours: string | null
}

export interface PublicSocialLink {
  platform: SocialPlatform
  label: string | null
  url: string
}

/** Identidad pública de la instalación. businessName es null hasta que se configure. */
export interface PublicSite {
  businessName: string | null
  tagline: string | null
  /** Principal: se usa en todos los temas. */
  logo: SiteLogo | null
  /** Variante opcional para fondo oscuro. */
  logoOnDark: SiteLogo | null
  contact: SiteContact
  socialLinks: PublicSocialLink[]
}

/** Configuración pública de la instalación. Sin cookies, como el resto de la API pública. */
export async function fetchPublicSite(signal?: AbortSignal): Promise<PublicSite> {
  let response: Response
  try {
    response = await fetch('/api/public/site', {
      credentials: 'omit',
      headers: { Accept: 'application/json' },
      signal,
    })
  } catch (error) {
    if (signal?.aborted) throw error
    throw new ApiError(0, 'No se pudo conectar con el servidor. Revisa tu conexión.')
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as PublicSite
}
