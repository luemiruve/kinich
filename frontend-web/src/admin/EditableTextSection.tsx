import { useState } from 'react'

import * as adminApi from '@/api/adminPage'
import type { AdminSection, AdminTextBlock, TextBlockValues, TextSectionKey } from '@/api/adminPage'
import { Button } from '@/components/Button'
import { SECTION_LABELS } from '@/content/sectionLabels'

import { blockLabel } from './blockLabel'
import { ConfirmDeleteDialog } from './ConfirmDeleteDialog'
import { SectionHeadingEditor } from './SectionHeadingEditor'
import { SectionPublishBar } from './SectionPublishBar'
import { TextBlockForm } from './TextBlockForm'
import { TextBlockItem } from './TextBlockItem'
import { useSectionEditor } from './useSectionEditor'

interface EditableTextSectionProps {
  section: AdminSection
  reload: () => Promise<void>
  notify: (message: string) => void
  /** Avisa a la página de que esta sección tiene un formulario abierto (bloquea el cambio de modo). */
  onFormOpenChange: (sectionKey: string, open: boolean) => void
}

const toValues = (b: AdminTextBlock): TextBlockValues => ({ eyebrow: b.eyebrow ?? '', title: b.title ?? '', body: b.body })

export function EditableTextSection({ section, reload, notify, onFormOpenChange }: EditableTextSectionProps) {
  const key = section.sectionKey as TextSectionKey
  const label = SECTION_LABELS[section.sectionKey]
  const { editing, formOpen, busy, fieldErrors, setFieldErrors, run, startCreate, startEdit, close, root, heading } =
    useSectionEditor<AdminTextBlock>({ sectionKey: section.sectionKey, reload, notify, onFormOpenChange })
  const [deleting, setDeleting] = useState<AdminTextBlock | null>(null)
  const blocks = [...section.texts].sort((a, b) => a.position - b.position)
  const full = section.maxBlocks !== null && blocks.length >= section.maxBlocks

  async function save(values: TextBlockValues) {
    setFieldErrors({})
    const ok = editing?.mode === 'edit'
      ? await run(() => adminApi.updateTextBlock(key, editing.target.id, values, editing.target.version), 'Borrador guardado')
      : await run(() => adminApi.createTextBlock(key, values), 'Borrador guardado')
    if (ok) close()
  }

  function move(index: number, direction: -1 | 1) {
    const ids = blocks.map((b) => b.id)
    ;[ids[index], ids[index + direction]] = [ids[index + direction], ids[index]]
    void run(() => adminApi.reorderTextBlocks(key, section.revision, ids), 'Orden guardado en el borrador')
  }

  return (
    <section ref={root} aria-label={label} className="flex flex-col gap-4 px-4 py-10 sm:px-6 md:px-16">
      <h2 ref={heading} tabIndex={-1} className="font-serif text-2xl">{label}</h2>
      <SectionPublishBar section={section} disabled={formOpen || busy} onPublish={() => void run(() => adminApi.publishSection(key, section.revision), 'Sección publicada')} />
      <SectionHeadingEditor section={section} reload={reload} notify={notify} onFormOpenChange={onFormOpenChange}
        disabled={formOpen || busy} />
      <ul className="flex flex-col gap-6">
        {blocks.map((block, index) =>
          editing?.mode === 'edit' && editing.target.id === block.id ? (
            <li key={block.id}>
              <TextBlockForm initial={toValues(block)} fieldErrors={fieldErrors} onSubmit={save} onCancel={close} />
            </li>
          ) : (
            <TextBlockItem
              key={block.id}
              block={block}
              disabled={formOpen || busy}
              canMoveUp={index > 0}
              canMoveDown={index < blocks.length - 1}
              onEdit={() => startEdit(block)}
              onMove={(direction) => move(index, direction)}
              onToggleHidden={() => void run(
                () => adminApi.setTextBlockVisibility(key, block.id, !block.hidden, block.version),
                block.hidden ? 'Se mostrará al publicar' : 'Se ocultará al publicar',
              )}
              onDelete={() => setDeleting(block)}
            />
          ),
        )}
      </ul>
      {editing?.mode === 'create' ? (
        <TextBlockForm fieldErrors={fieldErrors} onSubmit={save} onCancel={close} />
      ) : (
        <Button variant="text" className="self-start" data-new-item disabled={full || formOpen || busy}
          onClick={startCreate}>
          Nuevo elemento
        </Button>
      )}
      {deleting && (
        <ConfirmDeleteDialog
          label={blockLabel(deleting)}
          onCancel={() => setDeleting(null)}
          onConfirm={() => {
            const target = deleting
            setDeleting(null)
            void run(() => adminApi.deleteTextBlock(key, target.id, target.version), 'Eliminado del borrador')
          }}
        />
      )}
    </section>
  )
}
