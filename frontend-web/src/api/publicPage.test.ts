import { describe, expect, it, vi } from 'vitest'

import { ApiError } from './http'
import { fetchPublicHome } from './publicPage'

describe('fetchPublicHome', () => {
  it('no envía cookies y devuelve la página', async () => {
    const page = { pageKey: 'home', sections: [] }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(page), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchPublicHome()).resolves.toEqual(page)
    expect(fetchMock).toHaveBeenCalledWith('/api/public/pages/home', expect.objectContaining({ credentials: 'omit' }))
  })

  it('convierte un error HTTP en ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 500 })))

    await expect(fetchPublicHome()).rejects.toBeInstanceOf(ApiError)
  })

  it('sin conexión devuelve un ApiError de estado 0', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')))

    await expect(fetchPublicHome()).rejects.toMatchObject({ status: 0 })
  })
})
