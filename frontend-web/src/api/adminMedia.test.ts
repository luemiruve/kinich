import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createCategory, fetchMedia, uploadMedia } from './adminMedia'
import * as http from './http'

vi.mock('./http', async (original) => ({ ...(await original<typeof import('./http')>()), request: vi.fn() }))

describe('adminMedia', () => {
  beforeEach(() => {
    vi.mocked(http.request).mockReset()
    vi.mocked(http.request).mockResolvedValue({})
  })

  it('sube el archivo en el campo file con POST multipart', async () => {
    const file = new File(['x'], 'foto.jpg', { type: 'image/jpeg' })

    await uploadMedia(file)

    const [path, init] = vi.mocked(http.request).mock.calls[0]!
    expect(path).toBe('/api/admin/media')
    expect(init!.method).toBe('POST')
    expect((init!.body as FormData).get('file')).toBe(file)
  })

  it('lista medios y crea categorías', async () => {
    await fetchMedia()
    await createCategory('Boda')

    expect(vi.mocked(http.request).mock.calls[0]![0]).toBe('/api/admin/media')
    expect(vi.mocked(http.request).mock.calls[1]).toEqual([
      '/api/admin/content-categories', { method: 'POST', body: JSON.stringify({ label: 'Boda' }) },
    ])
  })
})
