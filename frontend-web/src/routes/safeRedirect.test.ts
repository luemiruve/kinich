import { describe, expect, it } from 'vitest'

import { safeRedirect } from './safeRedirect'

describe('safeRedirect', () => {
  it.each(['/admin', '/admin/inicio', '/admin/inicio-2'])('acepta rutas del panel: %s', (path) => {
    expect(safeRedirect(path)).toBe(path)
  })

  it.each([
    'https://evil.example',
    '//evil.example',
    '/admin//evil.example',
    '/admin/../otra',
    '/otra',
    'javascript:alert(1)',
    null,
    ['/admin'],
  ])('rechaza destinos externos o inválidos: %s', (value) => {
    expect(safeRedirect(value)).toBe('/admin')
  })
})
