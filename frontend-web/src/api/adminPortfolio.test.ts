import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as http from './http'
import {
  createProject, deleteProject, publishOrder, publishProject, reorderProjects, setProjectVisibility, updateProject,
  type ProjectValues,
} from './adminPortfolio'
import { publishSection, updateSectionHeading } from './adminPage'

vi.mock('./http', async (original) => ({ ...(await original<typeof http>()), request: vi.fn() }))
const request = vi.mocked(http.request)

const values: ProjectValues = {
  slug: 'boda', title: 'Boda', eyebrow: '', categoryId: null, description: '',
  cover: { mediaId: 'm1', alt: 'Portada', crop: null },
  images: [{ mediaId: 'm2', alt: 'Uno', caption: '' }],
}
const body = {
  slug: 'boda', title: 'Boda', eyebrow: null, categoryId: null, description: null,
  cover: { mediaId: 'm1', alt: 'Portada', crop: null },
  images: [{ mediaId: 'm2', alt: 'Uno', caption: null }],
}

describe('API de administración de Portafolio', () => {
  beforeEach(() => request.mockResolvedValue(undefined))

  it('crear y guardar envían el agregado completo; los opcionales vacíos van como null', async () => {
    await createProject(values)
    await updateProject('p1', values, 3)
    expect(request).toHaveBeenCalledWith('/api/admin/portfolio/projects', { method: 'POST', body: JSON.stringify(body) })
    expect(request).toHaveBeenCalledWith('/api/admin/portfolio/projects/p1', {
      method: 'PUT', body: JSON.stringify({ ...body, version: 3 }),
    })
  })

  it('visibilidad, publicación y eliminación envían la versión', async () => {
    await setProjectVisibility('p1', true, 4)
    await publishProject('p1', 5)
    await deleteProject('p1', 6)
    expect(request).toHaveBeenCalledWith('/api/admin/portfolio/projects/p1/visibility', {
      method: 'PATCH', body: JSON.stringify({ hidden: true, version: 4 }),
    })
    expect(request).toHaveBeenCalledWith('/api/admin/portfolio/projects/p1/publication', {
      method: 'PUT', body: JSON.stringify({ version: 5 }),
    })
    expect(request).toHaveBeenCalledWith('/api/admin/portfolio/projects/p1?version=6', { method: 'DELETE' })
  })

  it('el orden se guarda y se publica con la lista completa de versiones', async () => {
    const refs = [{ id: 'b', version: 2 }, { id: 'a', version: 1 }]
    await reorderProjects(refs)
    await publishOrder(refs)
    expect(request).toHaveBeenCalledWith('/api/admin/portfolio/projects/order', {
      method: 'PUT', body: JSON.stringify({ projects: refs }),
    })
    expect(request).toHaveBeenCalledWith('/api/admin/portfolio/order/publication', {
      method: 'PUT', body: JSON.stringify({ projects: refs }),
    })
  })

  it('los textos de la página usan las rutas de la página portfolio', async () => {
    await updateSectionHeading('page-header', { eyebrow: '', title: 'Portafolio', body: '' }, 2, 'portfolio')
    await publishSection('closing-cta', 4, 'portfolio')
    expect(request).toHaveBeenCalledWith('/api/admin/pages/portfolio/sections/page-header/heading', {
      method: 'PUT', body: JSON.stringify({ eyebrow: null, title: 'Portafolio', body: null, revision: 2 }),
    })
    expect(request).toHaveBeenCalledWith('/api/admin/pages/portfolio/sections/closing-cta/publication', {
      method: 'PUT', body: JSON.stringify({ revision: 4 }),
    })
  })
})
