import { request } from './http'
import type { Crop, FocalPoint, PublicImage, SectionHeading, SectionKey } from './publicPage'

export type ContentStatus = 'DRAFT' | 'PUBLISHED' | 'HIDDEN'
export type TextSectionKey = 'editorial-note' | 'values'
export type ItemSectionKey = 'hero' | 'disciplines' | 'featured-selection' | 'closing-cta'
/** Mismos nombres que el JSON de entrada del backend. */
export type ItemFieldName = 'eyebrow' | 'categoryId' | 'title' | 'description'
/** Campos de cabecera que admite una sección (los define el backend). */
export type HeadingFieldName = 'eyebrow' | 'title' | 'body'
/** Cómo se encuadra la imagen en la sección: rectángulo 4:5 o punto focal. */
export type ItemFraming = 'CROP_4_5' | 'FOCAL_POINT'
/** Páginas con secciones editables en el panel (slug de la ruta del backend). */
export type PageKey = 'home' | 'portfolio'
/** Secciones de Portafolio: solo cabecera editorial (D: el CMS solo gestiona PAGE_HEADER y CLOSING_CTA). */
export type PortfolioSectionKey = 'page-header' | 'closing-cta'
/** Cualquier sección con cabecera editable, de cualquier página. */
export type HeadingSectionKey = SectionKey | PortfolioSectionKey

export interface AdminTextBlock {
  id: string
  eyebrow: string | null
  title: string | null
  body: string
  hidden: boolean
  position: number
  version: number
  status: ContentStatus
  modified: boolean
}

export interface AdminItem {
  id: string
  image: PublicImage
  mediaId: string
  category: { id: string; label: string } | null
  eyebrow: string | null
  title: string | null
  description: string | null
  hidden: boolean
  position: number
  version: number
  status: ContentStatus
  modified: boolean
}

export interface AdminSection {
  sectionKey: SectionKey
  textEditable: boolean
  maxBlocks: number | null
  itemEditable: boolean
  maxItems: number | null
  itemFields: ItemFieldName[]
  requiredItemFields: ItemFieldName[]
  itemFraming: ItemFraming | null
  headingFields: HeadingFieldName[]
  /** Borrador de la cabecera (se publica con la sección). */
  heading: SectionHeading
  revision: number
  publishedRevision: number
  hasUnpublishedChanges: boolean
  publishedAt: string | null
  texts: AdminTextBlock[]
  items: AdminItem[]
}

export interface AdminPage {
  pageKey: 'home'
  sections: AdminSection[]
}

/** En Portafolio las secciones solo tienen cabecera (sin textos ni elementos). */
export type AdminPortfolioSection = Omit<AdminSection, 'sectionKey'> & { sectionKey: PortfolioSectionKey }

export interface AdminPortfolioPage {
  pageKey: 'portfolio'
  sections: AdminPortfolioSection[]
}

export interface TextBlockValues {
  eyebrow: string
  title: string
  body: string
}

export interface HeadingValues {
  eyebrow: string
  title: string
  body: string
}

export interface ItemValues {
  mediaId: string
  imageAlt: string
  categoryId: string | null
  eyebrow: string
  title: string
  description: string
  /** null = encuadre centrado por defecto (lo calcula el servidor). */
  crop: Crop | null
  mediaPosition: FocalPoint | null
}

const pageBase = (page: PageKey) => `/api/admin/pages/${page}`
const BASE = pageBase('home')
const texts = (section: TextSectionKey) => `${BASE}/sections/${section}/texts`
const items = (section: ItemSectionKey) => `${BASE}/sections/${section}/items`

/** Los opcionales vacíos van como null: los campos que la sección no admite nunca llegan con valor. */
const toItemBody = (values: ItemValues) => ({
  mediaId: values.mediaId,
  imageAlt: values.imageAlt,
  categoryId: values.categoryId,
  eyebrow: values.eyebrow || null,
  title: values.title || null,
  description: values.description || null,
  crop: values.crop,
  mediaPosition: values.mediaPosition,
})

export function fetchAdminHome(signal?: AbortSignal): Promise<AdminPage> {
  return request<AdminPage>(BASE, { signal })
}

export function fetchAdminPortfolioPage(signal?: AbortSignal): Promise<AdminPortfolioPage> {
  return request<AdminPortfolioPage>(pageBase('portfolio'), { signal })
}

export function createTextBlock(section: TextSectionKey, values: TextBlockValues): Promise<AdminTextBlock> {
  return request<AdminTextBlock>(texts(section), { method: 'POST', body: JSON.stringify(values) })
}

export function updateTextBlock(section: TextSectionKey, id: string, values: TextBlockValues, version: number): Promise<AdminTextBlock> {
  return request<AdminTextBlock>(`${texts(section)}/${id}`, { method: 'PUT', body: JSON.stringify({ ...values, version }) })
}

export function setTextBlockVisibility(section: TextSectionKey, id: string, hidden: boolean, version: number): Promise<AdminTextBlock> {
  return request<AdminTextBlock>(`${texts(section)}/${id}/visibility`, {
    method: 'PATCH',
    body: JSON.stringify({ hidden, version }),
  })
}

export function deleteTextBlock(section: TextSectionKey, id: string, version: number): Promise<void> {
  return request<void>(`${texts(section)}/${id}?version=${version}`, { method: 'DELETE' })
}

export function reorderTextBlocks(section: TextSectionKey, revision: number, ids: string[]): Promise<void> {
  return request<void>(`${texts(section)}/order`, { method: 'PUT', body: JSON.stringify({ revision, ids }) })
}

export function createItem(section: ItemSectionKey, values: ItemValues): Promise<AdminItem> {
  return request<AdminItem>(items(section), { method: 'POST', body: JSON.stringify(toItemBody(values)) })
}

export function updateItem(section: ItemSectionKey, id: string, values: ItemValues, version: number): Promise<AdminItem> {
  return request<AdminItem>(`${items(section)}/${id}`, { method: 'PUT', body: JSON.stringify({ ...toItemBody(values), version }) })
}

export function setItemVisibility(section: ItemSectionKey, id: string, hidden: boolean, version: number): Promise<AdminItem> {
  return request<AdminItem>(`${items(section)}/${id}/visibility`, { method: 'PATCH', body: JSON.stringify({ hidden, version }) })
}

export function deleteItem(section: ItemSectionKey, id: string, version: number): Promise<void> {
  return request<void>(`${items(section)}/${id}?version=${version}`, { method: 'DELETE' })
}

export function reorderItems(section: ItemSectionKey, revision: number, ids: string[]): Promise<void> {
  return request<void>(`${items(section)}/order`, { method: 'PUT', body: JSON.stringify({ revision, ids }) })
}

export function publishSection(section: TextSectionKey | ItemSectionKey | PortfolioSectionKey, revision: number,
  page: PageKey = 'home'): Promise<void> {
  return request<void>(`${pageBase(page)}/sections/${section}/publication`, { method: 'PUT', body: JSON.stringify({ revision }) })
}

/** Los campos vacíos van como null (borran el texto); los que la sección no admite nunca llegan con valor. */
export function updateSectionHeading(section: HeadingSectionKey, values: HeadingValues, revision: number,
  page: PageKey = 'home'): Promise<unknown> {
  return request(`${pageBase(page)}/sections/${section}/heading`, {
    method: 'PUT',
    body: JSON.stringify({
      eyebrow: values.eyebrow || null,
      title: values.title || null,
      body: values.body || null,
      revision,
    }),
  })
}
