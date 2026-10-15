import type { AdminSection } from '@/api/adminPage'
import type { AdminPortfolioPage, AdminPortfolioSection } from '@/api/adminPage'
import type { AdminProject, AdminProjectSummary } from '@/api/adminPortfolio'
import type { PublicImage } from '@/api/publicPage'

/** Datos de prueba compartidos por los tests del panel de Portafolio. */
export const image = (id: string, extra: Partial<PublicImage> = {}): PublicImage => ({
  alt: `Alt ${id}`, width: 1600, height: 2000,
  sources: [{ type: 'image/avif', candidates: [{ url: `/api/admin/media/files/a-${id}`, width: 480 }] }],
  fallback: { type: 'image/jpeg', candidates: [{ url: `/api/admin/media/files/j-${id}`, width: 480 }] },
  crop: { x: 0, y: 0, width: 1, height: 1 }, mediaPosition: null, ...extra,
})

export const summary = (id: string, position: number, extra: Partial<AdminProjectSummary> = {}): AdminProjectSummary => ({
  id, slug: `proyecto-${id}`, title: `Proyecto ${id}`, category: null, cover: image(id), status: 'DRAFT',
  modified: false, hidden: false, position, version: position, ...extra,
})

export const project = (extra: Partial<AdminProject> = {}): AdminProject => ({
  id: 'p1', slug: 'elena-carlos', publishedSlug: null, title: 'Elena y Carlos', eyebrow: 'Mérida, 2026',
  category: { id: 'c1', label: 'Bodas' }, description: 'Una boda en la hacienda.',
  cover: image('cover', { alt: 'Portada de la boda' }), coverMediaId: 'm-cover',
  images: [
    { mediaId: 'm-1', image: image('g1', { alt: 'Primer baile', crop: null }), caption: 'El primer baile' },
    { mediaId: 'm-2', image: image('g2', { alt: 'Brindis', crop: null }), caption: null },
  ],
  status: 'DRAFT', modified: false, hidden: false, position: 1, version: 3, ...extra,
})

export const media = (id: string) => ({
  id, contentType: 'image/jpeg', width: 1600, height: 1067, sizeBytes: 1000, createdAt: '2026-10-10T10:00:00Z',
  renditions: {
    sources: [{ type: 'image/avif', candidates: [{ url: `/api/admin/media/files/a-${id}`, width: 480 }] }],
    fallback: { type: 'image/jpeg', candidates: [{ url: `/api/admin/media/files/j-${id}`, width: 480 }] },
  },
})

const section = (sectionKey: AdminPortfolioSection['sectionKey'], extra: Partial<AdminSection> = {}): AdminPortfolioSection => ({
  textEditable: false, maxBlocks: null, itemEditable: false, maxItems: null, itemFields: [], requiredItemFields: [],
  itemFraming: null, headingFields: ['eyebrow', 'title', 'body'], heading: { eyebrow: null, title: null, body: null },
  revision: 1, publishedRevision: 0, hasUnpublishedChanges: true, publishedAt: null, texts: [], items: [], ...extra,
  sectionKey,
})

export const portfolioPage = (): AdminPortfolioPage => ({
  pageKey: 'portfolio',
  sections: [
    section('page-header', { heading: { eyebrow: null, title: 'Historias', body: null }, revision: 2 }),
    section('closing-cta', { revision: 4 }),
  ],
})
