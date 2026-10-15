import { Link } from 'react-router'

/** Proyecto desconocido o no publicado (404 de la API): vista propia, sin redirigir a la portada. */
export function ProjectNotFound() {
  return (
    <div className="mx-auto flex min-h-[60svh] max-w-md flex-col items-center justify-center gap-6 px-4 text-center">
      <h1 className="font-serif text-4xl break-words">Proyecto no encontrado</h1>
      <p className="text-on-surface-variant">Es posible que el enlace sea antiguo o que el proyecto ya no esté publicado.</p>
      <Link to="/portafolio" className="label-caps inline-flex min-h-11 items-center text-primary underline underline-offset-4">
        Volver al portafolio
      </Link>
    </div>
  )
}
