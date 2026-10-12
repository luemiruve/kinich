/// <reference types="vite/client" />
import { describe, expect, it } from 'vitest'

import css from '@/index.css?raw'

/** Lee los tokens --kn-* de un bloque de index.css. */
function tokens(block: string): Record<string, string> {
  return Object.fromEntries([...block.matchAll(/--kn-([\w-]+):\s*(#[0-9a-f]{6})/gi)].map((m) => [m[1], m[2]]))
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function ratio(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

const light = tokens(css.slice(css.indexOf(':root {'), css.indexOf('@media (prefers-color-scheme: dark)')))
const dark = { ...light, ...tokens(css.slice(css.indexOf('@media (prefers-color-scheme: dark)'), css.indexOf('@theme inline'))) }

describe.each([
  ['claro', light],
  ['oscuro', dark],
])('contraste WCAG 2.2 AA en modo %s', (_, t) => {
  it.each([
    ['on-surface', 'surface', 4.5],
    ['on-surface-variant', 'surface', 4.5],
    ['on-surface-variant', 'surface-low', 4.5],
    ['secondary', 'surface', 4.5], // eyebrow y enlaces (texto)
    ['secondary', 'surface-low', 3], // foco sobre campos
    ['on-primary', 'primary-container', 4.5], // botón
    ['on-primary', 'primary', 4.5], // botón en hover
    ['outline', 'surface', 3], // bordes de controles
    ['error', 'surface', 4.5],
    ['on-feature-panel', 'feature-panel', 4.5],
    ['on-feature-panel-muted', 'feature-panel', 4.5],
  ])('%s sobre %s ≥ %s:1', (fg, bg, min) => {
    expect(ratio(t[fg], t[bg])).toBeGreaterThanOrEqual(min)
  })

  it('no conserva tokens fuera de la paleta', () => {
    expect(Object.keys(t)).not.toContain('accent')
    expect(Object.keys(t)).not.toContain('accent-strong')
    expect(Object.keys(t)).not.toContain('hairline')
  })
})

it('el bloque oscuro se lee y difiere del claro', () => {
  expect(dark.surface).not.toBe(light.surface)
  expect(dark['on-surface']).not.toBe(light['on-surface'])
})

it('la superficie clara es la de la paleta', () => {
  expect(light.surface).toBe('#fbf9f6')
})
