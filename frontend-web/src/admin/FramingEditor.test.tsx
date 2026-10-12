import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { ImageSource } from '@/api/publicPage'

import { FramingEditor } from './FramingEditor'

const renditions = {
  sources: [] as ImageSource[],
  fallback: { type: 'image/jpeg', candidates: [{ url: '/api/admin/media/files/f-480', width: 480 }] },
}

describe('FramingEditor', () => {
  it('rectángulo 4:5: zoom y posición con controles nativos etiquetados que emiten un encuadre válido', () => {
    const onChange = vi.fn()
    render(<FramingEditor framing="CROP_4_5" image={{ width: 3000, height: 2000, ...renditions }} crop={null}
      mediaPosition={null} onChange={onChange} />)

    expect(screen.getByRole('group', { name: 'Encuadre' })).toBeInTheDocument()
    const zoom = screen.getByRole('slider', { name: 'Acercar' })
    const horizontal = screen.getByRole('slider', { name: 'Posición horizontal' })
    expect(screen.getByRole('slider', { name: 'Posición vertical' })).toBeDisabled() // sin margen vertical sin zoom
    fireEvent.change(zoom, { target: { value: '2' } })
    fireEvent.change(horizontal, { target: { value: '0' } })

    const last = onChange.mock.calls.at(-1)![0]
    expect(last.mediaPosition).toBeNull()
    expect(last.crop.x).toBe(0)
    expect((last.crop.width * 3000) / (last.crop.height * 2000)).toBeCloseTo(0.8, 6)
    expect(horizontal).toHaveAttribute('aria-valuetext', '0 %')
  })

  it('la vista previa reproduce el encuadre elegido', () => {
    render(<FramingEditor framing="CROP_4_5" image={{ width: 3000, height: 2000, ...renditions }}
      crop={{ x: 0, y: 0, width: 0.5, height: 1 }} mediaPosition={null} onChange={vi.fn()} />)

    expect(screen.getByRole('img', { name: 'Vista previa del encuadre' }).style.width).toBe('200%')
  })

  it('punto focal: dos controles y vistas previas en huecos horizontal y vertical', async () => {
    const onChange = vi.fn()
    render(<FramingEditor framing="FOCAL_POINT" image={{ width: 3000, height: 2000, ...renditions }} crop={null}
      mediaPosition={{ x: 0.5, y: 0.5 }} onChange={onChange} />)

    fireEvent.change(screen.getByRole('slider', { name: 'Punto focal horizontal' }), { target: { value: '20' } })
    expect(onChange).toHaveBeenLastCalledWith({ crop: null, mediaPosition: { x: 0.2, y: 0.5 } })
    const previews = screen.getAllByRole('img', { name: /Vista previa/ })
    expect(previews).toHaveLength(2)
    expect(previews[0].style.objectPosition).toBe('20% 50%')
  })

  it('los controles se alcanzan con el teclado', async () => {
    const user = userEvent.setup()
    render(<FramingEditor framing="FOCAL_POINT" image={{ width: 3000, height: 2000, ...renditions }} crop={null}
      mediaPosition={null} onChange={vi.fn()} />)

    await user.tab()
    expect(screen.getByRole('slider', { name: 'Punto focal horizontal' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('slider', { name: 'Punto focal vertical' })).toHaveFocus()
  })
})
