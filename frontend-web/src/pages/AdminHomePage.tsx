import { useEffect, useState } from 'react'
import { Link } from 'react-router'

import { fetchAdminSettings } from '@/api/adminSettings'

import { Button } from '@/components/Button'
import { useSession } from '@/session/useSession'

/** Destino tras iniciar sesión: accesos a la edición de Inicio y Portafolio y a la configuración de la instalación. */
export function AdminHomePage() {
  const { user, logout } = useSession()
  const [logoutFailed, setLogoutFailed] = useState(false)
  const [identityConfigured, setIdentityConfigured] = useState(true)

  // Aviso de instalación sin identidad. Si la consulta falla no se muestra nada: el panel sigue siendo usable.
  useEffect(() => {
    const controller = new AbortController()
    fetchAdminSettings(controller.signal)
      .then((settings) => setIdentityConfigured(settings.identityConfigured))
      .catch(() => undefined)
    return () => controller.abort()
  }, [])

  async function handleLogout() {
    setLogoutFailed(false)
    try {
      await logout()
    } catch {
      setLogoutFailed(true)
    }
  }

  return (
    <div className="min-h-dvh">
      <header className="flex items-center justify-between gap-4 border-b border-outline-variant px-4 py-2 sm:px-6 md:px-16">
        <span className="label-caps">Kinich · Estudio</span>
        <Button variant="text" onClick={() => void handleLogout()}>
          Cerrar sesión
        </Button>
      </header>

      {logoutFailed && (
        <p role="alert" className="bg-error-container px-4 py-2.5 text-sm leading-5 text-on-error-container sm:px-6 md:px-16">
          No se pudo cerrar la sesión. Revisa tu conexión e inténtalo de nuevo.
        </p>
      )}

      <main className="mx-auto w-full max-w-4xl px-4 py-12 sm:px-6 md:py-14">
        <p className="label-caps mb-2 text-secondary">Mi sitio</p>
        <h1 className="font-serif text-3xl leading-10 break-words sm:text-4xl sm:leading-[3.125rem]">
          Hola, {user?.fullName}
        </h1>
        {!identityConfigured && (
          <p className="mt-6 border border-secondary px-4 py-3 text-sm">
            La identidad del negocio todavía no está configurada.{' '}
            <Link to="/admin/configuracion" className="underline decoration-secondary underline-offset-4">Configurarla</Link>
          </p>
        )}
        <nav aria-label="Panel" className="mt-6 flex flex-wrap gap-x-6">
          <Link to="/admin/inicio" className="inline-flex min-h-11 items-center label-caps underline decoration-secondary underline-offset-4">
            Editar Inicio
          </Link>
          <Link to="/admin/portafolio" className="inline-flex min-h-11 items-center label-caps underline decoration-secondary underline-offset-4">
            Editar Portafolio
          </Link>
          <Link to="/admin/configuracion" className="inline-flex min-h-11 items-center label-caps underline decoration-secondary underline-offset-4">
            Configuración
          </Link>
        </nav>
      </main>
    </div>
  )
}
