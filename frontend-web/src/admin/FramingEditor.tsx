import { useId, useState } from 'react'

import type { ItemFraming } from '@/api/adminPage'
import type { Crop, FocalPoint, ImageSource } from '@/api/publicPage'
import { MediaFrame } from '@/components/editorial/MediaFrame'
import { labelClassName } from '@/components/form/fieldStyles'
import { IMAGE_SIZES } from '@/content/imageSizes'

import { controlsFromCrop, cropFromControls, type CropControls, MAX_ZOOM } from './framing'

export interface FramingImage {
  width: number
  height: number
  sources: ImageSource[]
  fallback: ImageSource
}

interface FramingEditorProps {
  framing: ItemFraming
  image: FramingImage
  crop: Crop | null
  mediaPosition: FocalPoint | null
  onChange: (value: { crop: Crop | null; mediaPosition: FocalPoint | null }) => void
}

const percentText = (value: number) => `${Math.round(value * 100)} %`
const ZOOM_STEP = 0.05

/**
 * Encuadre del elemento con controles nativos (`input type=range`): operables por teclado, etiquetados y de
 * 44 px. Solo cambia datos del elemento: el original y sus representaciones no se modifican ni se regeneran.
 */
export function FramingEditor({ framing, image, crop, mediaPosition, onChange }: FramingEditorProps) {
  return (
    <fieldset className="flex flex-col gap-3 border border-outline-variant p-4">
      <legend className={`${labelClassName} px-1`}>Encuadre</legend>
      <p className="text-sm text-on-surface-variant">El encuadre no modifica la fotografía original.</p>
      {framing === 'CROP_4_5' ? (
        <CropControlsPanel image={image} crop={crop} onChange={(next) => onChange({ crop: next, mediaPosition: null })} />
      ) : (
        <FocalPointPanel image={image} value={mediaPosition ?? { x: 0.5, y: 0.5 }}
          onChange={(next) => onChange({ crop: null, mediaPosition: next })} />
      )}
    </fieldset>
  )
}

function Slider({ label, value, min, max, step, valueText, disabled, onChange }: {
  label: string; value: number; min: number; max: number; step: number; valueText: string; disabled?: boolean
  onChange: (value: number) => void
}) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className={labelClassName}>{label}</label>
      <input id={id} type="range" min={min} max={max} step={step} value={value} disabled={disabled}
        aria-valuetext={valueText} onChange={(e) => onChange(Number(e.target.value))}
        className="h-11 w-full cursor-pointer disabled:cursor-not-allowed disabled:opacity-60" />
    </div>
  )
}

function CropControlsPanel({ image, crop, onChange }: { image: FramingImage; crop: Crop | null; onChange: (crop: Crop) => void }) {
  const [controls, setControls] = useState<CropControls>(() => controlsFromCrop(image.width, image.height, crop))
  const current = cropFromControls(image.width, image.height, controls)
  const update = (patch: Partial<CropControls>) => {
    const next = { ...controls, ...patch }
    setControls(next)
    onChange(cropFromControls(image.width, image.height, next))
  }
  return (
    <div className="flex flex-col gap-4 sm:flex-row">
      <MediaFrame image={{ ...image, alt: 'Vista previa del encuadre', crop: current, mediaPosition: null }}
        sizes={IMAGE_SIZES.framingPreview} className="aspect-[4/5] w-full max-w-48 shrink-0" />
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <Slider label="Acercar" min={1} max={MAX_ZOOM} step={ZOOM_STEP} value={Math.round(controls.zoom / ZOOM_STEP) * ZOOM_STEP}
          valueText={percentText(controls.zoom)} onChange={(zoom) => update({ zoom })} />
        <Slider label="Posición horizontal" min={0} max={100} step={1} value={Math.round(controls.x * 100)}
          valueText={percentText(controls.x)} disabled={current.width >= 1 - 1e-9}
          onChange={(x) => update({ x: x / 100 })} />
        <Slider label="Posición vertical" min={0} max={100} step={1} value={Math.round(controls.y * 100)}
          valueText={percentText(controls.y)} disabled={current.height >= 1 - 1e-9}
          onChange={(y) => update({ y: y / 100 })} />
      </div>
    </div>
  )
}

function FocalPointPanel({ image, value, onChange }: { image: FramingImage; value: FocalPoint; onChange: (value: FocalPoint) => void }) {
  const [point, setPoint] = useState(value)
  const update = (patch: Partial<FocalPoint>) => {
    const next = { ...point, ...patch }
    setPoint(next)
    onChange(next)
  }
  // El hueco de Hero y Cierre cambia de proporción: se previsualiza en uno horizontal y otro vertical.
  const preview = (alt: string, className: string) => (
    <MediaFrame image={{ ...image, alt, crop: null, mediaPosition: point }} sizes={IMAGE_SIZES.framingPreview}
      className={className} />
  )
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-3">
        {preview('Vista previa en un hueco horizontal', 'aspect-video w-full max-w-64')}
        {preview('Vista previa en un hueco vertical', 'aspect-[3/4] w-full max-w-32')}
      </div>
      <Slider label="Punto focal horizontal" min={0} max={100} step={1} value={Math.round(point.x * 100)}
        valueText={percentText(point.x)} onChange={(x) => update({ x: x / 100 })} />
      <Slider label="Punto focal vertical" min={0} max={100} step={1} value={Math.round(point.y * 100)}
        valueText={percentText(point.y)} onChange={(y) => update({ y: y / 100 })} />
    </div>
  )
}
