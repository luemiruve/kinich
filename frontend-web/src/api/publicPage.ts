import { ApiError, toApiError } from './http'

export type SectionKey = 'hero' | 'editorial-note' | 'disciplines' | 'values' | 'featured-selection' | 'closing-cta'

export interface PublicTextBlock {
  id: string
  eyebrow: string | null
  title: string | null
  body: string
}

/** Un candidato de srcset: URL de una representación web y su ancho real. */
export interface ImageCandidate {
  url: string
  width: number
}

/** Representaciones de un formato, ordenadas por ancho. */
export interface ImageSource {
  type: string
  candidates: ImageCandidate[]
}

/** Rectángulo de encuadre normalizado (0–1) sobre el original. */
export interface Crop {
  x: number
  y: number
  width: number
  height: number
}

/** Punto focal (mediaPosition) normalizado (0–1). */
export interface FocalPoint {
  x: number
  y: number
}

/**
 * Imagen lista para <picture>: dimensiones del original (proporción), formatos prioritarios (sources, AVIF) y
 * respaldo (fallback: JPEG, o PNG si la imagen tiene transparencia), más el encuadre del elemento. Nunca el original.
 */
export interface PublicImage {
  alt: string
  width: number
  height: number
  sources: ImageSource[]
  fallback: ImageSource
  crop: Crop | null
  mediaPosition: FocalPoint | null
}

/** Elemento audiovisual publicado: la categoría llega como su etiqueta (instantánea autosuficiente). */
export interface PublicItem {
  id: string
  image: PublicImage
  category: string | null
  eyebrow: string | null
  title: string | null
  description: string | null
}

/** Textos de cabecera de una sección (contenido editorial). Cada sección admite solo algunos campos. */
export interface SectionHeading {
  eyebrow: string | null
  title: string | null
  body: string | null
}

export interface PublicSection {
  sectionKey: SectionKey
  /** null si la sección no tiene cabecera publicada. */
  heading: SectionHeading | null
  texts: PublicTextBlock[]
  items: PublicItem[]
}

export interface PublicPage {
  pageKey: 'home'
  sections: PublicSection[]
}

/** Página pública: solo contenido publicado. Sin cookies, para no mezclarla nunca con la sesión. */
export async function fetchPublicHome(signal?: AbortSignal): Promise<PublicPage> {
  let response: Response
  try {
    response = await fetch('/api/public/pages/home', {
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
  return (await response.json()) as PublicPage
}
