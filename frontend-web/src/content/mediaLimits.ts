/** Límites de subida y de campos. Deben coincidir con el backend (MediaService, ImageInspector, V4–V6). */
export const MEDIA_LIMITS = {
  maxBytes: 10_485_760,
  maxDimension: 8000,
  acceptedTypes: ['image/jpeg', 'image/png', 'image/webp', 'image/avif'],
  imageAlt: 300,
  categoryLabel: 60,
  description: 2000,
} as const
