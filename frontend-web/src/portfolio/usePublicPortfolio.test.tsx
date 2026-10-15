import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/api/http'
import type { PublicPortfolioPage } from '@/api/publicPortfolio'

import { usePublicPortfolio } from './usePublicPortfolio'

const page: PublicPortfolioPage = { pageHeader: null, closingCta: null, categories: [], projects: [] }

describe('usePublicPortfolio', () => {
  it('pasa de cargando a listo', async () => {
    const load = vi.fn().mockResolvedValue(page)
    const { result } = renderHook(() => usePublicPortfolio(load))

    expect(result.current.state.status).toBe('loading')
    await waitFor(() => expect(result.current.state).toEqual({ status: 'ready', page }))
  })

  it('muestra el error y permite reintentar', async () => {
    const load = vi.fn().mockRejectedValueOnce(new ApiError(500, 'Ocurrió un error inesperado.')).mockResolvedValue(page)
    const { result } = renderHook(() => usePublicPortfolio(load))

    await waitFor(() => expect(result.current.state).toEqual({ status: 'error', message: 'Ocurrió un error inesperado.' }))
    act(() => result.current.retry())
    await waitFor(() => expect(result.current.state.status).toBe('ready'))
    expect(load).toHaveBeenCalledTimes(2)
  })
})
