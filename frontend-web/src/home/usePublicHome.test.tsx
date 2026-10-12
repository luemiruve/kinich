import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/api/http'
import type { PublicPage } from '@/api/publicPage'

import { usePublicHome } from './usePublicHome'

const page: PublicPage = { pageKey: 'home', sections: [] }

describe('usePublicHome', () => {
  it('pasa de cargando a listo', async () => {
    const load = vi.fn().mockResolvedValue(page)
    const { result } = renderHook(() => usePublicHome(load))

    expect(result.current.state.status).toBe('loading')
    await waitFor(() => expect(result.current.state).toEqual({ status: 'ready', page }))
  })

  it('muestra el error y permite reintentar', async () => {
    const load = vi.fn().mockRejectedValueOnce(new ApiError(500, 'Ocurrió un error inesperado.')).mockResolvedValue(page)
    const { result } = renderHook(() => usePublicHome(load))

    await waitFor(() => expect(result.current.state.status).toBe('error'))
    act(() => result.current.retry())
    await waitFor(() => expect(result.current.state.status).toBe('ready'))
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('al reintentar vuelve a cargando hasta que resuelve la segunda carga', async () => {
    let resolveSecond: (p: PublicPage) => void = () => {}
    const load = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(500, 'fallo'))
      .mockReturnValueOnce(new Promise<PublicPage>((resolve) => { resolveSecond = resolve }))
    const { result } = renderHook(() => usePublicHome(load))

    await waitFor(() => expect(result.current.state.status).toBe('error'))
    act(() => result.current.retry())
    expect(result.current.state.status).toBe('loading')
    await act(async () => resolveSecond(page))
    expect(result.current.state).toEqual({ status: 'ready', page })
  })

  it('ignora la respuesta tardía de una carga abortada', async () => {
    const pageA: PublicPage = { pageKey: 'home', sections: [{ sectionKey: 'hero', heading: null, texts: [], items: [] }] }
    const pageB: PublicPage = { pageKey: 'home', sections: [{ sectionKey: 'values', heading: null, texts: [], items: [] }] }
    let resolveA: (p: PublicPage) => void = () => {}
    let resolveB: (p: PublicPage) => void = () => {}
    const loadA = () => new Promise<PublicPage>((resolve) => { resolveA = resolve })
    const loadB = () => new Promise<PublicPage>((resolve) => { resolveB = resolve })
    const { result, rerender } = renderHook(({ load }) => usePublicHome(load), { initialProps: { load: loadA } })

    rerender({ load: loadB })
    await act(async () => resolveB(pageB))
    await act(async () => resolveA(pageA))

    expect(result.current.state).toEqual({ status: 'ready', page: pageB })
  })

  it('cancela la petición al desmontar', () => {
    let signal: AbortSignal | undefined
    const load = (s: AbortSignal) => {
      signal = s
      return new Promise<PublicPage>(() => {})
    }
    const { unmount } = renderHook(() => usePublicHome(load))

    unmount()
    expect(signal?.aborted).toBe(true)
  })
})
