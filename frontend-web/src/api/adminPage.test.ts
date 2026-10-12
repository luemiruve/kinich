import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as http from './http'
import { deleteTextBlock, publishSection, reorderTextBlocks, updateTextBlock } from './adminPage'

vi.mock('./http', async (original) => ({ ...(await original<typeof http>()), request: vi.fn() }))
const request = vi.mocked(http.request)

describe('API de administración de Inicio', () => {
  beforeEach(() => request.mockResolvedValue(undefined))

  it('editar envía la versión', async () => {
    await updateTextBlock('values', 'id-1', { eyebrow: '', title: 'T', body: 'B' }, 3)
    expect(request).toHaveBeenCalledWith('/api/admin/pages/home/sections/values/texts/id-1', {
      method: 'PUT',
      body: JSON.stringify({ eyebrow: '', title: 'T', body: 'B', version: 3 }),
    })
  })

  it('eliminar envía la versión en la query', async () => {
    await deleteTextBlock('values', 'id-1', 4)
    expect(request).toHaveBeenCalledWith('/api/admin/pages/home/sections/values/texts/id-1?version=4', { method: 'DELETE' })
  })

  it('ordenar y publicar envían la revisión', async () => {
    await reorderTextBlocks('values', 7, ['b', 'a'])
    await publishSection('values', 7)
    expect(request).toHaveBeenCalledWith('/api/admin/pages/home/sections/values/texts/order', {
      method: 'PUT',
      body: JSON.stringify({ revision: 7, ids: ['b', 'a'] }),
    })
    expect(request).toHaveBeenCalledWith('/api/admin/pages/home/sections/values/publication', {
      method: 'PUT',
      body: JSON.stringify({ revision: 7 }),
    })
  })
})
