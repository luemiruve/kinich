import { request } from './http'
import type { ImageSource } from './publicPage'

export interface MediaItem {
  id: string
  contentType: string
  width: number
  height: number
  sizeBytes: number
  createdAt: string
  renditions: { sources: ImageSource[]; fallback: ImageSource }
}

export interface Category {
  id: string
  label: string
}

/** El navegador pone el Content-Type multipart con su boundary; el servidor ignora el nombre y el tipo declarados. */
export function uploadMedia(file: File): Promise<MediaItem> {
  const body = new FormData()
  body.append('file', file)
  return request<MediaItem>('/api/admin/media', { method: 'POST', body })
}

export function fetchMedia(signal?: AbortSignal): Promise<MediaItem[]> {
  return request<MediaItem[]>('/api/admin/media', { signal })
}

export function fetchCategories(signal?: AbortSignal): Promise<Category[]> {
  return request<Category[]>('/api/admin/content-categories', { signal })
}

export function createCategory(label: string): Promise<Category> {
  return request<Category>('/api/admin/content-categories', { method: 'POST', body: JSON.stringify({ label }) })
}
