// Variante de Inicio: texto a la izquierda, imagen a la derecha; sin botones de acción
import type { PublicItem, SectionHeading } from '@/api/publicPage'
import { ClosingCta } from '@/components/editorial/ClosingCta'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { IMAGE_SIZES } from '@/content/imageSizes'

/** Textos (contenido editorial publicado) y foto publicada. Sin ninguno de los dos, la sección no se muestra. */
export function ClosingCtaSection({ heading, item }: { heading: SectionHeading | null; item?: PublicItem }) {
  if (!heading && !item) return null
  return <ClosingCta heading={heading} media={<MediaFrame image={item?.image} sizes={IMAGE_SIZES.halfFromMd} className="min-h-72" />} />
}
