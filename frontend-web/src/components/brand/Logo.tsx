import type { SiteLogo } from '@/api/site'

interface LogoProps {
  /** Nombre del negocio: texto alternativo del logotipo o, sin logotipo, el texto visible. */
  name: string
  /** Principal: se usa en todos los temas (un SVG preparado con prefers-color-scheme cubre ambos). */
  logo: SiteLogo | null
  /** Variante explícita para fondo oscuro; sin ella, el modo oscuro usa el principal. */
  logoOnDark: SiteLogo | null
  /** Alto de renderizado en px: con la proporción del viewBox fija ancho y alto (sin salto de layout). */
  maxHeight: number
  /** Alto visual (clases de Tailwind); el ancho sale de la proporción, sin deformar. */
  className?: string
}

const widthFor = (logo: SiteLogo, height: number) => Math.round((height * logo.width) / logo.height)

/**
 * Logotipo de la instalación (SVG configurable desde el panel, nunca fijo en el código). Se usa como imagen: nunca
 * se inserta el SVG en el HTML ni se recolorea. El navegador elige la variante oscura con `<source media>` y la
 * cambia en vivo, sin JavaScript. Sin logotipo principal, el nombre en texto.
 */
export function Logo({ name, logo, logoOnDark, maxHeight, className = 'h-8' }: LogoProps) {
  if (!logo) {
    return <span className="font-serif text-xl">{name}</span>
  }
  return (
    // block: sin hueco de línea bajo la imagen (la cabecera mantiene su alto).
    <picture className="block">
      {logoOnDark && (
        <source media="(prefers-color-scheme: dark)" type="image/svg+xml" srcSet={logoOnDark.url}
          width={widthFor(logoOnDark, maxHeight)} height={maxHeight} />
      )}
      <img src={logo.url} alt={name} width={widthFor(logo, maxHeight)} height={maxHeight} decoding="async"
        className={`block w-auto max-w-full ${className}`} />
    </picture>
  )
}
