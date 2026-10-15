import type { CSSProperties } from 'react'

import type { PublicImage } from '@/api/publicPage'
import { scaleSizes } from '@/content/imageSizes'

import { defaultSrc, srcSet } from './srcSet'

interface MediaFrameProps {
  image?: PublicImage | null
  /** `sizes` del hueco donde se pinta (ancho real de renderizado), para que el navegador elija el candidato. */
  sizes: string
  className?: string
  /** Imagen principal de la página (Hero): sin carga diferida. */
  priority?: boolean
  /** Estilo del contenedor (p. ej. `aspect-ratio` para respetar la proporción original). */
  style?: CSSProperties
}

/** Porcentaje CSS con 4 decimales como máximo y sin ceros sobrantes. */
const percent = (value: number) => `${Number(value.toFixed(4))}%`

/**
 * Imagen publicada como <picture> (AVIF prioritario; respaldo JPEG, o PNG si tiene transparencia) o, sin imagen, una superficie tonal
 * decorativa fuera del árbol accesible. El encuadre del elemento se aplica solo con CSS: punto focal con
 * object-position o rectángulo escalando y desplazando la imagen completa. Nunca se piden archivos recortados.
 */
export function MediaFrame({ image, sizes, className = '', priority = false, style: frameStyle }: MediaFrameProps) {
  if (!image) {
    return <div aria-hidden="true" style={frameStyle} className={`bg-surface-container ${className}`} />
  }
  const crop = image.crop
  const factor = crop ? 1 / crop.width : 1
  const effectiveSizes = scaleSizes(sizes, factor)
  const style: CSSProperties = crop
    ? {
        position: 'absolute',
        maxWidth: 'none',
        width: percent(100 / crop.width),
        height: 'auto',
        left: percent(-(crop.x / crop.width) * 100 || 0),
        top: percent(-(crop.y / crop.height) * 100 || 0),
      }
    : {
        objectPosition: `${percent((image.mediaPosition?.x ?? 0.5) * 100)} ${percent((image.mediaPosition?.y ?? 0.5) * 100)}`,
      }
  return (
    <div style={frameStyle} className={`relative overflow-hidden bg-surface-container ${className}`}>
      <picture>
        {image.sources.map((source) => (
          <source key={source.type} type={source.type} srcSet={srcSet(source)} sizes={effectiveSizes} />
        ))}
        <img
          src={defaultSrc(image.fallback)}
          srcSet={srcSet(image.fallback)}
          sizes={effectiveSizes}
          alt={image.alt}
          width={image.width}
          height={image.height}
          decoding="async"
          {...(priority ? {} : { loading: 'lazy' as const })}
          style={style}
          className={crop ? undefined : 'h-full w-full object-cover'}
        />
      </picture>
    </div>
  )
}
