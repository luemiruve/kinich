import { ApiError, toApiError } from './http'
import type { PublicImage, SectionHeading } from './publicPage'

/** Tarjeta del listado: la portada llega con su recorte 4:5 (crop). */
export interface PublicProjectCard {
  slug: string
  title: string
  eyebrow: string | null
  category: string | null
  description: string | null
  cover: PublicImage
}

export interface PublicPortfolioPage {
  /** null mientras no haya cabecera publicada. */
  pageHeader: SectionHeading | null
  closingCta: SectionHeading | null
  /** Etiquetas presentes en proyectos publicados, en orden alfabético. */
  categories: string[]
  projects: PublicProjectCard[]
}

/** Listado público de Portafolio: solo contenido publicado. Sin cookies, como la portada. */
export async function fetchPublicPortfolio(signal?: AbortSignal): Promise<PublicPortfolioPage> {
  let response: Response
  try {
    response = await fetch('/api/public/pages/portfolio', {
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
  return (await response.json()) as PublicPortfolioPage
}
