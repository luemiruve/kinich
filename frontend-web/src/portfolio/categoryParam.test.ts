import { describe, expect, it } from 'vitest'

import { categoryParam } from './categoryParam'

const categories = ['Bodas', 'Retratos']
const read = (query: string) => categoryParam.read(new URLSearchParams(query), categories)

describe('categoryParam.read', () => {
  it('devuelve la etiqueta publicada sin distinguir mayúsculas', () => {
    expect(read('categoria=bodas')).toBe('Bodas')
    expect(read('categoria=RETRATOS')).toBe('Retratos')
  })

  it('sin parámetro, vacío o desconocido equivale a «Todos» (null)', () => {
    expect(read('')).toBeNull()
    expect(read('categoria=')).toBeNull()
    expect(read('categoria=Eventos')).toBeNull()
  })
})
