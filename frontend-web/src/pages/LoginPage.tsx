import { useState, type FormEvent } from 'react'

import { ApiError } from '@/api/http'
import { Button } from '@/components/Button'
import { PasswordField } from '@/components/form/PasswordField'
import { TextField } from '@/components/form/TextField'
import { displayName } from '@/content/site'
import { useSession } from '@/session/useSession'
import { usePublicSite } from '@/site/usePublicSite'

const ERROR_ID = 'login-error'

/**
 * Acceso a la administración. El panel decorativo muestra la identidad configurada. "Recordar sesión", "¿Olvidaste tu contraseña?"
 * y el acceso de clientes no se incluyen: no forman parte de esta versión.
 * Tras iniciar sesión, la navegación la resuelve GuestOnly.
 */
export function LoginPage() {
  const { login } = useSession()
  const site = usePublicSite()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    if (email.trim() === '' || password === '') {
      setError('Ingresa tu correo y tu contraseña.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await login(email, password)
    } catch (cause) {
      setPassword('')
      setError(messageFor(cause))
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-6 short:py-4 sm:px-6 sm:py-10">
      <div className="flex w-full max-w-md flex-col border border-outline-variant bg-surface-lowest md:max-w-4xl md:flex-row">
        <aside
          aria-hidden="true"
          className="flex min-h-36 flex-col justify-end gap-2 bg-feature-panel p-6 text-on-feature-panel short:hidden sm:min-h-44 sm:p-8 md:min-h-0 md:w-5/12 md:p-10 md:short:flex md:short:justify-center"
        >
          <span className="h-px w-8 bg-on-feature-panel-muted" />
          {site?.tagline && <p className="font-serif text-lg leading-7 break-words italic">{site.tagline}</p>}
          {site && <p className="label-caps break-words text-on-feature-panel-muted">{displayName(site)}</p>}
        </aside>

        <section className="flex flex-1 flex-col gap-8 p-6 short:gap-5 sm:p-8 md:p-12 md:short:p-6">
          <header>
            <h1 className="font-serif text-[1.625rem] leading-9 sm:text-3xl sm:leading-10">Acceso al Estudio</h1>
            <p className="mt-2 text-sm leading-6 text-on-surface-variant">
              Ingresa tus credenciales para administrar tus galerías, historias y contenidos en vivo.
            </p>
          </header>

          <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
            <TextField
              label="Correo electrónico"
              type="email"
              name="email"
              autoComplete="username"
              inputMode="email"
              autoCapitalize="none"
              spellCheck={false}
              maxLength={254}
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              invalid={error !== null}
              describedBy={error ? ERROR_ID : undefined}
            />
            <PasswordField
              label="Contraseña"
              name="password"
              autoComplete="current-password"
              maxLength={128}
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              invalid={error !== null}
              describedBy={error ? ERROR_ID : undefined}
            />

            {error && (
              <p
                id={ERROR_ID}
                role="alert"
                className="bg-error-container px-4 py-2.5 text-sm leading-5 text-on-error-container"
              >
                {error}
              </p>
            )}

            <Button type="submit" disabled={submitting} className="mt-2">
              {submitting ? 'Verificando…' : 'Acceder al estudio'}
              <span aria-hidden="true">→</span>
            </Button>
          </form>

          <footer className="mt-auto border-t border-outline-variant pt-4 short:pt-3 text-center text-[0.6875rem] leading-4 text-on-surface-variant">
            Acceso cifrado y exclusivo para el estudio · Kinich
          </footer>
        </section>
      </div>
    </main>
  )
}

function messageFor(cause: unknown): string {
  if (!(cause instanceof ApiError)) return 'Ocurrió un error inesperado.'
  if (cause.status === 400) return 'Revisa el correo y la contraseña.'
  return cause.message
}
