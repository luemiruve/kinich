import { expect, it } from 'vitest'

import type { AdminItem, AdminPage, AdminSection, AdminTextBlock } from '@/api/adminPage'

import { draftToPublicPage } from './draftToPublicPage'

const block = (id: string, position: number, hidden = false): AdminTextBlock => ({
  id, eyebrow: null, title: null, body: id, hidden, position, version: 0, status: 'DRAFT', modified: false,
})

const section = (sectionKey: AdminSection['sectionKey']): AdminSection => ({
  sectionKey, textEditable: false, maxBlocks: null, itemEditable: false, maxItems: null, itemFields: [],
  requiredItemFields: [], itemFraming: null, headingFields: [], heading: { eyebrow: null, title: null, body: null }, revision: 3, publishedRevision: 0, hasUnpublishedChanges: true, publishedAt: null,
  texts: [], items: [],
})

const adminItem = (id: string, position: number, hidden = false): AdminItem => ({
  id, image: {
    alt: `Alt ${id}`, width: 4, height: 3, sources: [],
    fallback: { type: 'image/jpeg', candidates: [{ url: `/api/admin/media/files/${id}`, width: 4 }] },
    crop: { x: 0.2, y: 0, width: 0.6, height: 1 }, mediaPosition: null,
  }, mediaId: id,
  category: { id: 'c', label: 'Bodas' }, eyebrow: null, title: id, description: null, hidden, position, version: 0,
  status: 'DRAFT', modified: false,
})

it('la previsualización excluye ocultos y respeta el orden del borrador', () => {
  const admin: AdminPage = {
    pageKey: 'home',
    sections: [{
      ...section('values'),
      texts: [block('b', 2), block('oculto', 3, true), block('a', 1)],
    }],
  }

  expect(draftToPublicPage(admin).sections[0].texts.map((t) => t.body)).toEqual(['a', 'b'])
})

it('los elementos del borrador se previsualizan con la imagen de administración y la etiqueta de categoría', () => {
  const admin: AdminPage = {
    pageKey: 'home',
    sections: [{ ...section('disciplines'), items: [adminItem('b', 2), adminItem('oculto', 3, true), adminItem('a', 1)] }],
  }

  const items = draftToPublicPage(admin).sections[0].items
  expect(items.map((i) => i.title)).toEqual(['a', 'b'])
  expect(items[0].image.fallback.candidates[0].url).toBe('/api/admin/media/files/a')
  expect(items[0].image.crop).toEqual({ x: 0.2, y: 0, width: 0.6, height: 1 })
  expect(items[0].category).toBe('Bodas')
})

it('la cabecera del borrador se previsualiza; vacía equivale a no publicada', () => {
  const admin: AdminPage = {
    pageKey: 'home',
    sections: [
      { ...section('disciplines'), heading: { eyebrow: 'Servicios', title: 'Disciplinas', body: null } },
      section('values'),
    ],
  }

  const [disciplines, values] = draftToPublicPage(admin).sections
  expect(disciplines.heading).toEqual({ eyebrow: 'Servicios', title: 'Disciplinas', body: null })
  expect(values.heading).toBeNull()
})
