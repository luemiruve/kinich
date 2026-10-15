interface PortfolioFiltersProps {
  categories: string[]
  /** null = «Todos». */
  selected: string | null
  onSelect: (category: string | null) => void
}

const base = 'label-caps min-h-11 cursor-pointer px-4 transition-colors duration-200'
const active = 'bg-primary-container text-on-primary'
const inactive = 'bg-surface-container text-on-surface hover:bg-surface-low'

export function PortfolioFilters({ categories, selected, onSelect }: PortfolioFiltersProps) {
  return (
    <div role="group" aria-label="Filtrar por categoría" className="flex flex-wrap justify-center gap-2 px-4 sm:px-6 md:px-16">
      <button type="button" aria-pressed={selected === null} className={`${base} ${selected === null ? active : inactive}`} onClick={() => onSelect(null)}>
        Todos
      </button>
      {categories.map((category) => (
        <button
          key={category}
          type="button"
          aria-pressed={selected === category}
          className={`${base} ${selected === category ? active : inactive}`}
          onClick={() => onSelect(category)}
        >
          {category}
        </button>
      ))}
    </div>
  )
}
