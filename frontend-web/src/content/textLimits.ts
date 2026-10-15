/** Límites técnicos (D7). Deben coincidir con V3 y con los DTOs del backend. No son reglas de negocio. */
export const TEXT_LIMITS = { eyebrow: 120, title: 200, body: 2000 } as const

/** Límites técnicos de Portafolio: deben coincidir con `PortfolioLimits` del backend y con V12. */
export const PORTFOLIO_LIMITS = {
  projects: 200,
  images: 60,
  slug: 80,
  title: 200,
  eyebrow: 120,
  description: 2000,
  alt: 300,
  caption: 300,
} as const
