export const ADMIN_HOME = '/admin'

/** Solo se aceptan destinos internos del panel para evitar redirecciones abiertas. */
export function safeRedirect(value: unknown): string {
  return typeof value === 'string' && /^\/admin(\/[\w-]+)*$/.test(value) ? value : ADMIN_HOME
}
