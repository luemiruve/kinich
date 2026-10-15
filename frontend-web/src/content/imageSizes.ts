/**
 * `sizes` por hueco, derivados del maquetado de cada sección (márgenes despreciables frente al ancho de la columna).
 * Le dicen al navegador qué ancho real ocupará la imagen para elegir el candidato de srcset adecuado.
 */
export const IMAGE_SIZES = {
  /** Hero y Cierre: columna completa en móvil, media pantalla desde md. */
  halfFromMd: '(min-width: 768px) 50vw, 100vw',
  /** Selección destacada: diapositivas de 85 % / 45 % / 30 % del ancho. */
  featured: '(min-width: 1024px) 30vw, (min-width: 640px) 45vw, 85vw',
  /** Miniaturas del panel (128 px). */
  thumbnail: '128px',
  /** Rejilla del selector de imágenes: 2 / 3 / 4 columnas. */
  picker: '(min-width: 768px) 25vw, (min-width: 640px) 33vw, 50vw',
  /** Vista previa del editor de encuadre. */
  framingPreview: '(min-width: 640px) 320px, 80vw',
} as const

/** Disciplinas: la rejilla tiene tantas columnas como tarjetas (máximo 4) desde sm/lg. */
export function disciplinesSizes(count: number): string {
  if (count <= 1) return '100vw'
  if (count === 2) return '(min-width: 640px) 50vw, 100vw'
  return `(min-width: 1024px) ${count === 3 ? '33vw' : '25vw'}, (min-width: 640px) 50vw, 100vw`
}

/**
 * Con un rectángulo de encuadre la imagen se dibuja `1 / ancho` veces más grande que el hueco: cada longitud de
 * `sizes` se multiplica por ese factor para que el navegador no elija un candidato demasiado pequeño.
 */
export function scaleSizes(sizes: string, factor: number): string {
  if (factor === 1) return sizes
  return sizes
    .split(',')
    .map((entry) => {
      const match = /^\s*(\(.*\)\s+)?(\S.*?)\s*$/.exec(entry)!
      return `${match[1] ?? ''}calc(${match[2]} * ${Number(factor.toFixed(4))})`
    })
    .join(', ')
}

/** Tarjeta de Portafolio: rejilla de 1 / 2 / 3 columnas (móvil / sm / lg). */
export const PORTFOLIO_CARD_SIZES = '(min-width: 1024px) 33vw, (min-width: 640px) 50vw, 100vw'
