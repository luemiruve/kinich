import type { SectionHeading } from '@/api/publicPage'

import { Eyebrow } from './Eyebrow'

/** Cabecera centrada de página. El h1 siempre existe: sin título publicado se usa `fallbackTitle`. */
export function PageHeader({ heading, fallbackTitle }: { heading: SectionHeading | null; fallbackTitle: string }) {
  return (
    <header className="flex flex-col items-center gap-4 px-4 py-16 text-center sm:px-6 md:px-16">
      {heading?.eyebrow && <Eyebrow>{heading.eyebrow}</Eyebrow>}
      <h1 className="font-serif text-4xl break-words [overflow-wrap:anywhere] sm:text-5xl">{heading?.title || fallbackTitle}</h1>
      {heading?.body && (
        <>
          <div data-hairline aria-hidden="true" className="h-px w-16 bg-outline-variant" />
          <p className="max-w-prose whitespace-pre-line break-words [overflow-wrap:anywhere] font-serif text-lg italic text-on-surface-variant">
            {heading.body}
          </p>
        </>
      )}
    </header>
  )
}
