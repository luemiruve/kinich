import type { PublicItem } from '@/api/publicPage'
import { Eyebrow } from '@/components/editorial/Eyebrow'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { IMAGE_SIZES } from '@/content/imageSizes'

/**
 * El diseño no tiene titular visible: el h1 es solo para lectores de pantalla. La imagen y su información superior
 * (eyebrow) vienen del elemento publicado; sin elemento, superficie tonal y sin rótulo.
 */
export function HeroSection({ title, item }: { title: string; item?: PublicItem }) {
  return (
    <section data-section="hero" aria-labelledby="hero-title" className="grid grid-cols-1 min-h-[min(100svh,56rem)] md:grid-cols-2">
      <MediaFrame image={item?.image} sizes={IMAGE_SIZES.halfFromMd} priority className="min-w-0 min-h-[50svh] md:order-2 md:min-h-full" />
      <div className="flex min-w-0 flex-col justify-end gap-4 px-4 py-12 sm:px-6 md:px-16">
        {item?.eyebrow && <Eyebrow>{item.eyebrow}</Eyebrow>}
        <h1 id="hero-title" className="sr-only">
          {title}
        </h1>
      </div>
    </section>
  )
}
