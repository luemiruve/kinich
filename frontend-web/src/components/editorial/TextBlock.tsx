import { Eyebrow } from './Eyebrow'

interface TextBlockProps {
  block: { eyebrow: string | null; title: string | null; body: string }
  headingLevel?: 2 | 3
}

/** Bloque del formulario textual. Los opcionales vacíos no se pintan; el texto nunca se interpreta como HTML. */
export function TextBlock({ block, headingLevel = 3 }: TextBlockProps) {
  const Heading = headingLevel === 2 ? 'h2' : 'h3'
  return (
    <div data-text-block className="flex min-w-0 flex-col gap-3">
      {block.eyebrow && <Eyebrow>{block.eyebrow}</Eyebrow>}
      {block.title && <Heading className="font-serif text-2xl leading-8 break-words [overflow-wrap:anywhere]">{block.title}</Heading>}
      <p className="whitespace-pre-line break-words [overflow-wrap:anywhere] text-on-surface-variant">{block.body}</p>
    </div>
  )
}
