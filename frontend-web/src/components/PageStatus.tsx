/** Estado de página completa (p. ej. mientras se verifica la sesión). */
export function PageStatus({ message }: { message: string }) {
  return (
    <main className="flex min-h-dvh items-center justify-center px-4">
      <p role="status" className="label-caps text-on-surface-variant">
        {message}
      </p>
    </main>
  )
}
