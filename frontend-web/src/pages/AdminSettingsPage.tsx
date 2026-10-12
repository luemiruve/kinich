import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'

import { ContactForm } from '@/admin/settings/ContactForm'
import { IdentityForm } from '@/admin/settings/IdentityForm'
import { LogoSettings } from '@/admin/settings/LogoSettings'
import { SocialLinksEditor } from '@/admin/settings/SocialLinksEditor'
import { useSettings } from '@/admin/settings/useSettings'
import { Button } from '@/components/Button'
import { PageStatus } from '@/components/PageStatus'
import { ADMIN_HOME } from '@/routes/safeRedirect'

const sectionClass = 'flex flex-col gap-4 border-t border-outline-variant px-4 py-10 sm:px-6 md:px-16'

/**
 * Configuración de la instalación en tres áreas separadas (identidad, contacto, redes). No es contenido
 * editorial: cada área se aplica al guardar, sin borrador ni publicación.
 */
export function AdminSettingsPage() {
  const [notice, setNotice] = useState('')
  /** Un aviso idéntico al anterior cambia de contenido para que la región aria-live lo re-anuncie. */
  const notify = useCallback((message: string) => {
    setNotice((previous) => (previous === message ? `${message} ` : message))
  }, [])
  const { state, reload, run, conflicts } = useSettings(notify)

  useEffect(() => {
    document.title = 'Configuración · Kinich'
  }, [])

  if (state.status === 'loading') return <PageStatus message="Cargando configuración…" />
  if (state.status === 'error') {
    return (
      <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-4 px-4 text-center">
        <p role="alert">{state.message}</p>
        <Button className="w-auto" onClick={() => void reload()}>Reintentar</Button>
      </main>
    )
  }

  const { settings } = state
  return (
    <div className="min-h-dvh">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-outline-variant bg-surface px-4 py-2 sm:px-6 md:px-16">
        <Link to={ADMIN_HOME} className="label-caps min-h-11 content-center">← Panel</Link>
        <h1 className="font-serif text-xl">Configuración</h1>
      </header>
      <p role="status" aria-label="Avisos" aria-live="polite" className="px-4 py-2 text-sm sm:px-6 md:px-16">{notice}</p>
      <main className="mx-auto w-full max-w-4xl">
        {!settings.identityConfigured && (
          <p className="mx-4 border border-secondary px-4 py-3 text-sm sm:mx-6 md:mx-16">
            La identidad del negocio todavía no está configurada: faltan el nombre o el logotipo principal. Mientras
            tanto, el sitio muestra el nombre en texto (o el provisional «Kinich»).
          </p>
        )}
        <section aria-labelledby="settings-identity" className={sectionClass}>
          <h2 id="settings-identity" className="font-serif text-2xl">Identidad</h2>
          <IdentityForm key={`identity-${conflicts}`} settings={settings} run={run} />
          <h3 className="font-serif text-xl">Logotipo</h3>
          <LogoSettings settings={settings} run={run} />
        </section>
        <section aria-labelledby="settings-contact" className={sectionClass}>
          <h2 id="settings-contact" className="font-serif text-2xl">Contacto</h2>
          <ContactForm key={`contact-${conflicts}`} settings={settings} run={run} />
        </section>
        <section aria-labelledby="settings-social" className={sectionClass}>
          <h2 id="settings-social" className="font-serif text-2xl">Redes sociales</h2>
          <SocialLinksEditor key={`social-${conflicts}`} settings={settings} run={run} />
        </section>
      </main>
    </div>
  )
}
