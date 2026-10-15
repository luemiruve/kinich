import { describe, expect, it } from 'vitest'

import { slugify } from './slugify'

describe('slugify', () => {
  it('quita acentos, símbolos y guiones dobles', () => {
    expect(slugify('Elena & Carlos • Boda')).toBe('elena-carlos-boda')
    expect(slugify('  Ñandú en Mérida — 2026 ')).toBe('nandu-en-merida-2026')
    expect(slugify('---')).toBe('')
  })

  it('no supera 80 caracteres ni termina en guion', () => {
    const slug = slugify(`${'a'.repeat(79)} b`)
    expect(slug.length).toBeLessThanOrEqual(80)
    expect(slug).toMatch(/^[a-z0-9]+(-[a-z0-9]+)*$/)
  })
})
