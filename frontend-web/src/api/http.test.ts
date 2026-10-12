import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, request } from '@/api/http'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

describe('request', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=abc123; path=/'
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('envía el token CSRF en peticiones que modifican estado', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await request('/api/auth/logout', { method: 'POST' })

    const init = fetchMock.mock.calls[0]![1]!
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('abc123')
    expect(init.credentials).toBe('same-origin')
  })

  it('con FormData no fuerza JSON y envía el token CSRF', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 201 }))
    const body = new FormData()
    body.append('file', new Blob(['x']), 'x.png')

    await request('/api/admin/media', { method: 'POST', body })

    const headers = new Headers(fetchMock.mock.calls[0]![1]!.headers)
    expect(headers.get('Content-Type')).toBeNull()
    expect(headers.get('X-XSRF-TOKEN')).toBe('abc123')
  })

  it('sin cookie CSRF, escrituras simultáneas comparten una única petición del token', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    const pending: (() => void)[] = []
    fetchMock.mockImplementation((input) => {
      if (input === '/api/auth/me') {
        // Cada respuesta del servidor emitiría un token distinto: solo debe pedirse uno.
        return new Promise((resolve) => {
          pending.push(() => {
            document.cookie = `XSRF-TOKEN=emitido-${pending.length}; path=/`
            resolve(new Response(null, { status: 401 }))
          })
        })
      }
      return Promise.resolve(new Response(null, { status: 204 }))
    })

    const writes = [
      request('/api/admin/media', { method: 'POST', body: new FormData() }),
      request('/api/admin/media', { method: 'POST', body: new FormData() }),
    ]
    await vi.waitFor(() => expect(pending.length).toBeGreaterThan(0))
    await new Promise((resolve) => setTimeout(resolve, 0))
    pending.forEach((issue) => issue())
    await Promise.all(writes)

    const tokenRequests = fetchMock.mock.calls.filter(([input]) => input === '/api/auth/me')
    expect(tokenRequests).toHaveLength(1)
    const sent = fetchMock.mock.calls.filter(([input]) => input === '/api/admin/media')
      .map(([, init]) => new Headers(init!.headers).get('X-XSRF-TOKEN'))
    expect(sent).toEqual(['emitido-1', 'emitido-1'])
  })

  it('tras obtener el token, una petición posterior sin cookie vuelve a pedirlo', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    fetchMock.mockImplementation((input) => {
      if (input === '/api/auth/me') {
        document.cookie = 'XSRF-TOKEN=nuevo; path=/'
        return Promise.resolve(new Response(null, { status: 401 }))
      }
      return Promise.resolve(new Response(null, { status: 204 }))
    })

    await request('/api/auth/logout', { method: 'POST' })
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    await request('/api/auth/logout', { method: 'POST' })

    expect(fetchMock.mock.calls.filter(([input]) => input === '/api/auth/me')).toHaveLength(2)
  })

  it('no envía el token CSRF en peticiones de lectura', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { email: 'a@b.c', fullName: 'A' }))

    await request('/api/auth/me')

    const init = fetchMock.mock.calls[0]![1]!
    expect(new Headers(init.headers).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('convierte un ProblemDetail en ApiError', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(401, { status: 401, detail: 'Correo o contraseña incorrectos.' }),
    )

    await expect(request('/api/auth/login', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      status: 401,
      message: 'Correo o contraseña incorrectos.',
    })
  })

  it('informa de errores de red sin detalles técnicos', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    const error: unknown = await request('/api/auth/me').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(0)
  })

  it('expone el código estable del ProblemDetail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ status: 409, detail: 'El contenido cambió.', code: 'VERSION_CONFLICT' }),
      { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
    )))

    await expect(request('/api/x')).rejects.toMatchObject({ status: 409, code: 'VERSION_CONFLICT' })
  })
})
