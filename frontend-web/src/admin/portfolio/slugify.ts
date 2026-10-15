import { PORTFOLIO_LIMITS } from '@/content/textLimits'

/**
 * Slug sugerido a partir del título: sin acentos, minúsculas, números y guiones simples, como exige el backend
 * (`^[a-z0-9]+(-[a-z0-9]+)*$`, máximo 80). Es solo una sugerencia: el servidor valida y normaliza.
 */
export function slugify(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, PORTFOLIO_LIMITS.slug)
    .replace(/-+$/, '')
}
