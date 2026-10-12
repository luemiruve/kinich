import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { PublicImage } from '@/api/publicPage'

import { scaleSizes } from '@/content/imageSizes'

import { MediaFrame } from './MediaFrame'

const candidates = (format: string) =>
  [480, 768, 1280].map((width) => ({ url: `/api/public/media/files/${format}-${width}`, width }))

const image = (extra: Partial<PublicImage> = {}): PublicImage => ({
  alt: 'Mesa al aire libre',
  width: 3000,
  height: 2000,
  sources: [{ type: 'image/avif', candidates: candidates('avif') }],
  fallback: { type: 'image/jpeg', candidates: candidates('jpeg') },
  crop: null,
  mediaPosition: null,
  ...extra,
})

describe('MediaFrame', () => {
  it('ofrece AVIF con respaldo JPEG mediante srcset y sizes', () => {
    const { container } = render(<MediaFrame image={image()} sizes="(min-width: 768px) 50vw, 100vw" />)

    const source = container.querySelector('picture > source')!
    expect(source).toHaveAttribute('type', 'image/avif')
    expect(source).toHaveAttribute('srcset',
      '/api/public/media/files/avif-480 480w, /api/public/media/files/avif-768 768w, /api/public/media/files/avif-1280 1280w')
    expect(source).toHaveAttribute('sizes', '(min-width: 768px) 50vw, 100vw')
    const img = screen.getByRole('img', { name: 'Mesa al aire libre' })
    expect(img.getAttribute('srcset')).toContain('/api/public/media/files/jpeg-1280 1280w')
    expect(img).toHaveAttribute('sizes', '(min-width: 768px) 50vw, 100vw')
    expect(img).toHaveAttribute('src', '/api/public/media/files/jpeg-1280')
    expect(img).toHaveAttribute('width', '3000')
    expect(img).toHaveAttribute('height', '2000')
    expect(img).toHaveAttribute('loading', 'lazy')
  })

  it('el punto focal se aplica con object-position sobre una imagen que cubre el hueco', () => {
    render(<MediaFrame image={image({ mediaPosition: { x: 0.25, y: 0.8 } })} sizes="100vw" priority />)

    const img = screen.getByRole('img')
    expect(img).toHaveClass('object-cover')
    expect(img.style.objectPosition).toBe('25% 80%')
    expect(img).not.toHaveAttribute('loading')
  })

  it('el rectángulo se reproduce con CSS sin deformar: escala y desplaza la imagen completa', () => {
    const crop = { x: 0.25, y: 0, width: 0.5, height: 1 }
    const { container } = render(<MediaFrame image={image({ crop })} sizes="(min-width: 640px) 50vw, 100vw" className="aspect-[4/5]" />)

    const frame = container.firstElementChild!
    expect(frame).toHaveClass('relative', 'overflow-hidden', 'aspect-[4/5]')
    const img = screen.getByRole('img')
    expect(img.style.width).toBe('200%')
    expect(img.style.left).toBe('-50%')
    expect(img.style.top).toBe('0%')
    expect(img.style.height).toBe('auto')
    expect(img).toHaveAttribute('sizes', '(min-width: 640px) calc(50vw * 2), calc(100vw * 2)')
  })

  it('sin imagen pinta una superficie decorativa fuera del árbol accesible', () => {
    const { container } = render(<MediaFrame image={null} sizes="100vw" />)

    expect(container.firstElementChild).toHaveAttribute('aria-hidden', 'true')
    expect(screen.queryByRole('img')).toBeNull()
  })
})

describe('scaleSizes', () => {
  it('multiplica cada longitud y conserva las condiciones de medio', () => {
    expect(scaleSizes('(min-width: 1024px) 30vw, 85vw', 1.25)).toBe('(min-width: 1024px) calc(30vw * 1.25), calc(85vw * 1.25)')
    expect(scaleSizes('128px', 1)).toBe('128px')
  })
})
