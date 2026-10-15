import type { AdminSection } from '@/api/adminPage'
import { Button } from '@/components/Button'

const dateFormat = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' })

export function SectionPublishBar({ section, onPublish, disabled = false }: {
  section: Pick<AdminSection, 'publishedAt' | 'hasUnpublishedChanges'>; onPublish: () => void; disabled?: boolean }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-y border-outline-variant py-3">
      <p className="text-sm text-on-surface-variant">
        {section.publishedAt ? `Publicado: ${dateFormat.format(new Date(section.publishedAt))}` : 'Sin publicar'}
        {section.hasUnpublishedChanges && <strong className="ml-2 label-caps text-secondary">Cambios sin publicar</strong>}
      </p>
      <Button className="w-auto" disabled={disabled || !section.hasUnpublishedChanges} onClick={onPublish}>
        Publicar sección
      </Button>
    </div>
  )
}
