import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ClosingCta } from './ClosingCta'

describe('ClosingCta', () => {
  it('pinta eyebrow, título y cuerpo sin botones ni enlaces', () => {
    const { container } = render(
      <ClosingCta heading={{ eyebrow: 'Hablemos', title: 'Cuéntanos tu historia', body: 'Con calma.' }} />,
    )
    expect(screen.getByRole('heading', { level: 2, name: 'Cuéntanos tu historia' })).toBeInTheDocument()
    expect(screen.getByText('Hablemos')).toBeInTheDocument()
    expect(screen.getByText('Con calma.')).toBeInTheDocument()
    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.queryByRole('link')).toBeNull()
    expect(container.querySelector('svg')).toBeNull()
  })

  it('no se pinta sin textos ni medio', () => {
    const { container } = render(<ClosingCta heading={null} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('con solo medio se pinta', () => {
    render(<ClosingCta heading={null} media={<div data-testid="media" />} />)
    expect(screen.getByTestId('media')).toBeInTheDocument()
  })
})
