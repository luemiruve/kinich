import type { ReactNode } from 'react'

export function Eyebrow({ children }: { children: ReactNode }) {
  return <p data-eyebrow className="label-caps text-secondary">{children}</p>
}
