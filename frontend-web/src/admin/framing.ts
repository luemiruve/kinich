import type { Crop } from '@/api/publicPage'

/** Proporción 4:5 de Disciplinas y Selección (ancho / alto), igual que ItemFraming.CROP_4_5 en el backend. */
export const CROP_RATIO = 4 / 5
export const MAX_ZOOM = 4

/** Controles accesibles del rectángulo: zoom (1 = el mayor 4:5 posible) y posición 0–1 en cada eje. */
export interface CropControls {
  zoom: number
  x: number
  y: number
}

/** Mayor rectángulo 4:5 que cabe en la imagen, en píxeles del original. */
function base(width: number, height: number) {
  return width <= height * CROP_RATIO
    ? { w: width, h: width / CROP_RATIO }
    : { w: height * CROP_RATIO, h: height }
}

/** Rectángulo normalizado (0–1). El lado limitante sin zoom vale exactamente 1, como en el servidor. */
export function cropFromControls(width: number, height: number, { zoom, x, y }: CropControls): Crop {
  const { w, h } = base(width, height)
  const cropWidth = zoom === 1 && w === width ? 1 : w / zoom / width
  const cropHeight = zoom === 1 && h === height ? 1 : h / zoom / height
  return { x: (1 - cropWidth) * x, y: (1 - cropHeight) * y, width: cropWidth, height: cropHeight }
}

export function controlsFromCrop(width: number, height: number, crop: Crop | null): CropControls {
  if (!crop) return { zoom: 1, x: 0.5, y: 0.5 }
  const { w } = base(width, height)
  const position = (offset: number, size: number) => (size >= 1 - 1e-9 ? 0.5 : offset / (1 - size))
  const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value))
  return {
    zoom: clamp(w / (crop.width * width), 1, MAX_ZOOM),
    x: clamp(position(crop.x, crop.width), 0, 1),
    y: clamp(position(crop.y, crop.height), 0, 1),
  }
}
