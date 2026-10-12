/** Mensaje de error de un campo, enlazado con aria-describedby. */
export function FieldError({ id, message }: { id: string; message?: string }) {
  if (!message) return null
  return <p id={id} className="text-sm text-error">{message}</p>
}
