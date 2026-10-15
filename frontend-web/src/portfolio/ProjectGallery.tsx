import type { PublicProjectImage } from '@/api/publicPortfolio'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { PORTFOLIO_GALLERY_SIZES } from '@/content/imageSizes'

/** Imágenes en su proporción original (sin recorte ni salto de layout): 1 columna en móvil, 2 desde md, por filas. */
export function ProjectGallery({ images }: { images: PublicProjectImage[] }) {
  return (
    <div className="grid grid-cols-1 items-start gap-6 md:grid-cols-2">
      {images.map(({ image, caption }, index) => (
        <figure key={index} className="flex flex-col gap-2">
          <MediaFrame
            image={{ ...image, crop: null, mediaPosition: null }}
            sizes={PORTFOLIO_GALLERY_SIZES}
            className="w-full"
            style={{ aspectRatio: `${image.width} / ${image.height}` }}
          />
          {caption && <figcaption className="text-sm text-on-surface-variant">{caption}</figcaption>}
        </figure>
      ))}
    </div>
  )
}
