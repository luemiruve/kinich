import { useState } from 'react'

import * as adminApi from '@/api/adminPage'
import type { AdminItem, AdminSection, ItemSectionKey, ItemValues } from '@/api/adminPage'
import { Button } from '@/components/Button'
import { SECTION_LABELS } from '@/content/sectionLabels'

import { ConfirmDeleteDialog } from './ConfirmDeleteDialog'
import { ContentItemCard } from './ContentItemCard'
import { ContentItemForm } from './ContentItemForm'
import { itemLabel } from './itemLabel'
import { SectionHeadingEditor } from './SectionHeadingEditor'
import { SectionPublishBar } from './SectionPublishBar'
import { useSectionEditor } from './useSectionEditor'

interface EditableItemSectionProps {
  section: AdminSection
  reload: () => Promise<void>
  notify: (message: string) => void
  onFormOpenChange: (sectionKey: string, open: boolean) => void
}

const toValues = (item: AdminItem): ItemValues => ({
  mediaId: item.mediaId,
  imageAlt: item.image.alt,
  categoryId: item.category?.id ?? null,
  eyebrow: item.eyebrow ?? '',
  title: item.title ?? '',
  description: item.description ?? '',
  crop: item.image.crop,
  mediaPosition: item.image.mediaPosition,
})

/** Sección de elementos audiovisuales (Hero, Disciplinas, Selección destacada, Cierre): borrador → publicar. */
export function EditableItemSection({ section, reload, notify, onFormOpenChange }: EditableItemSectionProps) {
  const key = section.sectionKey as ItemSectionKey
  const label = SECTION_LABELS[section.sectionKey]
  const { editing, formOpen, busy, fieldErrors, setFieldErrors, run, startCreate, startEdit, close, root, heading } =
    useSectionEditor<AdminItem>({ sectionKey: section.sectionKey, reload, notify, onFormOpenChange })
  const [deleting, setDeleting] = useState<AdminItem | null>(null)
  const items = [...section.items].sort((a, b) => a.position - b.position)
  const full = section.maxItems !== null && items.length >= section.maxItems

  async function save(values: ItemValues) {
    setFieldErrors({})
    const ok = editing?.mode === 'edit'
      ? await run(() => adminApi.updateItem(key, editing.target.id, values, editing.target.version), 'Borrador guardado')
      : await run(() => adminApi.createItem(key, values), 'Borrador guardado')
    if (ok) close()
  }

  function move(index: number, direction: -1 | 1) {
    const ids = items.map((i) => i.id)
    ;[ids[index], ids[index + direction]] = [ids[index + direction], ids[index]]
    void run(() => adminApi.reorderItems(key, section.revision, ids), 'Orden guardado en el borrador')
  }

  const form = (item?: AdminItem) => (
    <ContentItemForm
      initial={item && toValues(item)}
      initialImage={item?.image}
      framing={section.itemFraming ?? 'FOCAL_POINT'}
      fields={section.itemFields}
      required={section.requiredItemFields}
      fieldErrors={fieldErrors}
      onSubmit={save}
      onCancel={close}
    />
  )

  return (
    <section ref={root} aria-label={label} className="flex flex-col gap-4 px-4 py-10 sm:px-6 md:px-16">
      <h2 ref={heading} tabIndex={-1} className="font-serif text-2xl">{label}</h2>
      <SectionPublishBar section={section} disabled={formOpen || busy} onPublish={() => void run(() => adminApi.publishSection(key, section.revision), 'Sección publicada')} />
      <SectionHeadingEditor section={section} reload={reload} notify={notify} onFormOpenChange={onFormOpenChange}
        disabled={formOpen || busy} />
      <ul className="flex flex-col gap-6">
        {items.map((item, index) =>
          editing?.mode === 'edit' && editing.target.id === item.id ? (
            <li key={item.id}>{form(item)}</li>
          ) : (
            <ContentItemCard
              key={item.id}
              item={item}
              disabled={formOpen || busy}
              canMoveUp={index > 0}
              canMoveDown={index < items.length - 1}
              onEdit={() => startEdit(item)}
              onMove={(direction) => move(index, direction)}
              onToggleHidden={() => void run(
                () => adminApi.setItemVisibility(key, item.id, !item.hidden, item.version),
                item.hidden ? 'Se mostrará al publicar' : 'Se ocultará al publicar',
              )}
              onDelete={() => setDeleting(item)}
            />
          ),
        )}
      </ul>
      {editing?.mode === 'create' ? (
        form()
      ) : (
        <Button variant="text" className="self-start" data-new-item disabled={full || formOpen || busy} onClick={startCreate}>
          Nuevo elemento
        </Button>
      )}
      {deleting && (
        <ConfirmDeleteDialog
          label={itemLabel(deleting)}
          onCancel={() => setDeleting(null)}
          onConfirm={() => {
            const target = deleting
            setDeleting(null)
            void run(() => adminApi.deleteItem(key, target.id, target.version), 'Eliminado del borrador')
          }}
        />
      )}
    </section>
  )
}
