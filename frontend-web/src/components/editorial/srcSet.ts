import type { ImageSource } from '@/api/publicPage'

/** src del <img>: el mayor candidato hasta 1280 px (navegadores sin srcset). */
const DEFAULT_SRC_WIDTH = 1280

export const srcSet = (source: ImageSource) => source.candidates.map((c) => `${c.url} ${c.width}w`).join(', ')

export function defaultSrc(source: ImageSource): string | undefined {
  const fitting = source.candidates.filter((c) => c.width <= DEFAULT_SRC_WIDTH)
  return (fitting.at(-1) ?? source.candidates[0])?.url
}
