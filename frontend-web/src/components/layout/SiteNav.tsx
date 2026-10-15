import { useRef, useState, type KeyboardEvent } from 'react'
import { NavLink, useLocation } from 'react-router'

const LINKS = [
  { to: '/', label: 'Inicio', end: true },
  { to: '/portafolio', label: 'Portafolio', end: false },
] as const

const MENU_ID = 'site-nav-menu'

/**
 * Navegación del sitio: Inicio y Portafolio. En móvil los enlaces viven en un panel bajo el header que abre el
 * botón «Menú»; desde md se muestran en línea. El menú queda abierto solo en la ruta donde se abrió, así cualquier
 * navegación (enlace, atrás/adelante o programática) lo cierra sin efectos. Sin animaciones.
 */
export function SiteNav() {
  const { pathname } = useLocation()
  const [openedAt, setOpenedAt] = useState<string | null>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const open = openedAt === pathname

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape' && open) {
      setOpenedAt(null)
      buttonRef.current?.focus()
    }
  }

  return (
    <nav aria-label="Principal" onKeyDown={onKeyDown}>
      <button
        ref={buttonRef}
        type="button"
        aria-expanded={open}
        aria-controls={MENU_ID}
        onClick={() => setOpenedAt(open ? null : pathname)}
        className="label-caps flex min-h-11 min-w-11 items-center justify-center border border-outline-variant px-4 text-on-surface md:hidden"
      >
        Menú
      </button>
      <ul
        id={MENU_ID}
        className={`${open ? 'flex' : 'hidden'} absolute inset-x-0 top-full z-20 flex-col border-b border-outline-variant bg-surface px-4 py-2 sm:px-6 md:static md:flex md:flex-row md:gap-8 md:border-0 md:bg-transparent md:p-0`}
      >
        {LINKS.map(({ to, label, end }) => (
          <li key={to}>
            <NavLink
              to={to}
              end={end}
              // Cierra también al pulsar el enlace de la página actual (no hay cambio de ruta que lo cierre).
              onClick={() => setOpenedAt(null)}
              className={({ isActive }) =>
                `label-caps flex min-h-11 items-center ${isActive ? 'text-primary' : 'text-on-surface-variant'}`}
            >
              {label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
