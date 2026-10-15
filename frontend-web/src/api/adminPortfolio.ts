import { request } from './http'
import type { Crop, PublicImage } from './publicPage'

/** Con instantánea → PUBLISHED (aunque esté oculto: sigue público hasta publicarlo); sin ella, HIDDEN o DRAFT. */
export type ProjectStatus = 'DRAFT' | 'PUBLISHED' | 'HIDDEN'

export interface ProjectCategory {
  id: string
  label: string
}

export interface AdminProjectSummary {
  id: string
  slug: string
  title: string
  category: ProjectCategory | null
  cover: PublicImage
  status: ProjectStatus
  modified: boolean
  hidden: boolean
  position: number
  version: number
}

/** Lista en orden de borrador; `orderModified`: el orden de los publicados difiere del público. */
export interface AdminProjectList {
  projects: AdminProjectSummary[]
  orderModified: boolean
}

/** Imagen de la galería: el texto alternativo va dentro de `image`. */
export interface AdminProjectImage {
  mediaId: string
  image: PublicImage
  caption: string | null
}

export interface AdminProject {
  id: string
  slug: string
  /** Slug de la versión publicada, si existe. */
  publishedSlug: string | null
  title: string
  eyebrow: string | null
  category: ProjectCategory | null
  description: string | null
  cover: PublicImage
  coverMediaId: string
  images: AdminProjectImage[]
  status: ProjectStatus
  modified: boolean
  hidden: boolean
  position: number
  version: number
}

/** Borrador completo tal como lo edita el formulario (los opcionales vacíos son ''). */
export interface ProjectValues {
  slug: string
  title: string
  eyebrow: string
  categoryId: string | null
  description: string
  /** crop null = encuadre 4:5 centrado (lo calcula el servidor). */
  cover: { mediaId: string; alt: string; crop: Crop | null }
  images: { mediaId: string; alt: string; caption: string }[]
}

/** Proyecto con la versión que el cliente vio (orden y publicación del orden). */
export interface ProjectRef {
  id: string
  version: number
}

const BASE = '/api/admin/portfolio'
const PROJECTS = `${BASE}/projects`

const toBody = (values: ProjectValues) => ({
  slug: values.slug,
  title: values.title,
  eyebrow: values.eyebrow || null,
  categoryId: values.categoryId,
  description: values.description || null,
  cover: { mediaId: values.cover.mediaId, alt: values.cover.alt, crop: values.cover.crop },
  images: values.images.map((image) => ({ mediaId: image.mediaId, alt: image.alt, caption: image.caption || null })),
})

export function fetchProjects(signal?: AbortSignal): Promise<AdminProjectList> {
  return request<AdminProjectList>(PROJECTS, { signal })
}

export function fetchProject(id: string, signal?: AbortSignal): Promise<AdminProject> {
  return request<AdminProject>(`${PROJECTS}/${id}`, { signal })
}

export function createProject(values: ProjectValues): Promise<AdminProject> {
  return request<AdminProject>(PROJECTS, { method: 'POST', body: JSON.stringify(toBody(values)) })
}

export function updateProject(id: string, values: ProjectValues, version: number): Promise<AdminProject> {
  return request<AdminProject>(`${PROJECTS}/${id}`, { method: 'PUT', body: JSON.stringify({ ...toBody(values), version }) })
}

/** Retirar = ocultar en el borrador: sigue público hasta que se publica el proyecto (D-16). */
export function setProjectVisibility(id: string, hidden: boolean, version: number): Promise<AdminProject> {
  return request<AdminProject>(`${PROJECTS}/${id}/visibility`, { method: 'PATCH', body: JSON.stringify({ hidden, version }) })
}

/** Eliminar es inmediato: borra el borrador y la versión publicada. */
export function deleteProject(id: string, version: number): Promise<void> {
  return request<void>(`${PROJECTS}/${id}?version=${version}`, { method: 'DELETE' })
}

/** Guarda el orden en el borrador; no cambia el orden público (D-15). */
export function reorderProjects(projects: ProjectRef[]): Promise<AdminProjectList> {
  return request<AdminProjectList>(`${PROJECTS}/order`, { method: 'PUT', body: JSON.stringify({ projects }) })
}

export function publishProject(id: string, version: number): Promise<AdminProject> {
  return request<AdminProject>(`${PROJECTS}/${id}/publication`, { method: 'PUT', body: JSON.stringify({ version }) })
}

/** Publica el orden GUARDADO en el borrador; la lista solo valida el conjunto y las versiones (R-1). */
export function publishOrder(projects: ProjectRef[]): Promise<AdminProjectList> {
  return request<AdminProjectList>(`${BASE}/order/publication`, { method: 'PUT', body: JSON.stringify({ projects }) })
}
