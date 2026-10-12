import { describe, expect, it } from 'vitest'

import { inputClassName } from './fieldStyles'

describe('inputClassName', () => {
  it('deja que aplique el contorno global :focus-visible', () => {
    expect(inputClassName).not.toContain('outline-none')
    expect(inputClassName).not.toContain('focus:border-secondary')
  })

  it('mantiene el borde de error y los 48px de alto', () => {
    expect(inputClassName).toContain('aria-invalid:border-error')
    expect(inputClassName).toContain('h-12')
  })
})
