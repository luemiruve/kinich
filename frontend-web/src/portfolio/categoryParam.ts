/** Nombre del parámetro de consulta del filtro: `?categoria=Etiqueta`. */
export const CATEGORY_PARAM = 'categoria'

export const categoryParam = {
  /**
   * Etiqueta de categoría pedida en la URL, comparada sin distinguir mayúsculas. Devuelve la etiqueta tal como
   * la publica el servidor, o null («Todos») si falta o ya no existe entre las categorías publicadas.
   */
  read(search: URLSearchParams, categories: string[]): string | null {
    const wanted = search.get(CATEGORY_PARAM)?.trim().toLocaleLowerCase('es')
    if (!wanted) return null
    return categories.find((category) => category.toLocaleLowerCase('es') === wanted) ?? null
  },
}
