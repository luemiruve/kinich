import { describe, expect, it } from 'vitest'

import { controlsFromCrop, cropFromControls, MAX_ZOOM } from './framing'

const ratio = (c: { width: number; height: number }, w: number, h: number) => (c.width * w) / (c.height * h)

describe('encuadre 4:5', () => {
  it('sin zoom y centrado es el mayor rectángulo 4:5 (igual que el servidor)', () => {
    const landscape = cropFromControls(6000, 4000, { zoom: 1, x: 0.5, y: 0.5 })
    expect(landscape.height).toBe(1)
    expect(landscape.width).toBeCloseTo(3200 / 6000)
    expect(landscape.x).toBeCloseTo((1 - 3200 / 6000) / 2)
    const portrait = cropFromControls(3000, 6000, { zoom: 1, x: 0.5, y: 0.5 })
    expect(portrait.width).toBe(1)
    expect(portrait.y).toBeCloseTo((1 - 3750 / 6000) / 2)
  })

  it('el zoom y la posición siempre producen 4:5 dentro de la imagen', () => {
    for (const zoom of [1, 1.7, MAX_ZOOM]) {
      for (const x of [0, 0.3, 1]) {
        const crop = cropFromControls(3000, 2000, { zoom, x, y: 1 })
        expect(ratio(crop, 3000, 2000)).toBeCloseTo(0.8, 6)
        expect(crop.x).toBeGreaterThanOrEqual(0)
        expect(crop.y).toBeGreaterThanOrEqual(0)
        expect(crop.x + crop.width).toBeLessThanOrEqual(1 + 1e-9)
        expect(crop.y + crop.height).toBeLessThanOrEqual(1 + 1e-9)
      }
    }
  })

  it('recupera los controles de un encuadre guardado', () => {
    const crop = cropFromControls(3000, 2000, { zoom: 2, x: 0.25, y: 0.75 })
    const controls = controlsFromCrop(3000, 2000, crop)
    expect(controls.zoom).toBeCloseTo(2)
    expect(controls.x).toBeCloseTo(0.25)
    expect(controls.y).toBeCloseTo(0.75)
    expect(controlsFromCrop(3000, 2000, null)).toEqual({ zoom: 1, x: 0.5, y: 0.5 })
  })
})
