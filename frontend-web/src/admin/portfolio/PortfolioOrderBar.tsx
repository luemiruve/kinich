import { Button } from '@/components/Button'

/**
 * Aparece solo si el orden guardado en el borrador difiere del público (D-15). Subir/Bajar ya guardan el orden,
 * así que «Publicar orden» siempre publica un orden guardado.
 */
export function PortfolioOrderBar({ orderModified, disabled, onPublish }: { orderModified: boolean; disabled: boolean; onPublish: () => void }) {
  if (!orderModified) return null
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-y border-outline-variant py-3">
      <p className="label-caps text-secondary">Orden con cambios sin publicar</p>
      <Button className="w-auto" disabled={disabled} onClick={onPublish}>Publicar orden</Button>
    </div>
  )
}
