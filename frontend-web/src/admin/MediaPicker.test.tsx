import { render, screen } from '@testing-library/react'
import { StrictMode, useState } from 'react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as mediaApi from '@/api/adminMedia'
import { ApiError } from '@/api/http'

import { MediaPicker } from './MediaPicker'

vi.mock('@/api/adminMedia', () => ({ uploadMedia: vi.fn(), fetchMedia: vi.fn() }))

const uploaded = {
  id: 'm2', contentType: 'image/png', width: 10, height: 10, sizeBytes: 5, createdAt: '2026-10-10T10:00:00Z',
  renditions: { sources: [], fallback: { type: 'image/jpeg', candidates: [{ url: '/api/admin/media/files/w2', width: 10 }] } },
}

describe('MediaPicker', () => {
  beforeEach(() => {
    vi.mocked(mediaApi.uploadMedia).mockReset()
    vi.mocked(mediaApi.fetchMedia).mockResolvedValue([])
  })

  it('rechaza en el cliente un archivo de más de 10 MB sin llamar a la API', async () => {
    const user = userEvent.setup()
    render(<MediaPicker onSelect={vi.fn()} onClose={vi.fn()} />)
    const big = new File([new Uint8Array(10_485_761)], 'grande.jpg', { type: 'image/jpeg' })
    await user.upload(await screen.findByLabelText(/Subir imagen/), big)
    expect(mediaApi.uploadMedia).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('10 MB')
  })

  it('sube un archivo válido y lo selecciona', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    vi.mocked(mediaApi.uploadMedia).mockResolvedValue(uploaded)
    render(<MediaPicker onSelect={onSelect} onClose={vi.fn()} />)
    await user.upload(await screen.findByLabelText(/Subir imagen/), new File(['x'], 'f.png', { type: 'image/png' }))
    expect(onSelect).toHaveBeenCalledWith(uploaded)
  })

  it('muestra el motivo del rechazo del servidor', async () => {
    const user = userEvent.setup()
    vi.mocked(mediaApi.uploadMedia).mockRejectedValue(new ApiError(400, 'La solicitud contiene datos inválidos.',
      { file: 'la imagen tiene una rotación pendiente: expórtala con la rotación aplicada' }, 'VALIDATION_FAILED'))
    render(<MediaPicker onSelect={vi.fn()} onClose={vi.fn()} />)
    await user.upload(await screen.findByLabelText(/Subir imagen/), new File(['x'], 'f.jpg', { type: 'image/jpeg' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('rotación pendiente')
  })

  it('avisa de que se suba una exportación para web', async () => {
    render(<MediaPicker onSelect={vi.fn()} onClose={vi.fn()} />)
    expect(await screen.findByText(/exportación para web/)).toBeInTheDocument()
  })

  it('al cerrar devuelve el foco a quien lo abrió, también con StrictMode y un showModal que mueve el foco', async () => {
    const user = userEvent.setup()
    const original = HTMLDialogElement.prototype.showModal
    const originalFocus = HTMLElement.prototype.focus
    // Como el navegador: showModal enfoca el primer control y, con el modal abierto, el resto de la página es inerte.
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.open = true
      this.querySelector<HTMLElement>('input, button')?.focus()
    }
    HTMLElement.prototype.focus = function focus(this: HTMLElement, options?: FocusOptions) {
      const modal = document.querySelector('dialog[open]')
      if (modal && !modal.contains(this)) return
      originalFocus.call(this, options)
    }
    function Harness() {
      const [open, setOpen] = useState(false)
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>Elegir imagen</button>
          {open && <MediaPicker onSelect={vi.fn()} onClose={() => setOpen(false)} />}
        </>
      )
    }
    try {
      render(<StrictMode><Harness /></StrictMode>)
      await user.click(screen.getByRole('button', { name: 'Elegir imagen' }))
      await user.click(await screen.findByRole('button', { name: 'Cancelar' }))
      expect(screen.getByRole('button', { name: 'Elegir imagen' })).toHaveFocus()
    } finally {
      HTMLDialogElement.prototype.showModal = original
      HTMLElement.prototype.focus = originalFocus
    }
  })
})
