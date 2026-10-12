import { useState } from 'react'

import { removeLogo, uploadLogo, type AdminSettings, type LogoVariant } from '@/api/adminSettings'
import { PRODUCT_NAME } from '@/content/site'

import { LogoField } from './LogoField'
import type { useSettings } from './useSettings'

type Run = ReturnType<typeof useSettings>['run']

/**
 * Logotipos SVG de la identidad. Cada subida o retirada se aplica al momento con la versión vigente de la
 * configuración (la misma fila que nombre y contacto): un 409 recarga como el resto de formularios.
 */
export function LogoSettings({ settings, run }: { settings: AdminSettings; run: Run }) {
  const [busy, setBusy] = useState(false)
  const [errors, setErrors] = useState<{ variant: LogoVariant; message: string } | null>(null)
  const { logo, logoOnDark, businessName } = settings.identity
  const name = businessName ?? PRODUCT_NAME

  async function apply(variant: LogoVariant, action: () => Promise<AdminSettings>, success: string) {
    setBusy(true)
    await run(action, success, (fieldErrors) => {
      const message = fieldErrors.file ?? fieldErrors.logo
      setErrors(message ? { variant, message } : null)
    })
    setBusy(false)
  }

  const field = (variant: LogoVariant) => ({
    onUpload: (file: File) => void apply(variant, () => uploadLogo(variant, file, settings.version), 'Logotipo actualizado'),
    onRemove: () => void apply(variant, () => removeLogo(variant, settings.version), 'Logotipo retirado'),
    error: errors?.variant === variant ? errors.message : undefined,
  })

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-on-surface-variant">
        Sube el logotipo en SVG preparado por la marca; se aplica al momento. Si el SVG ya se adapta al tema claro y
        al oscuro, basta con el principal. Si la marca tiene otro diseño para fondo oscuro, súbelo como variante.
      </p>
      <div className="grid gap-6 md:grid-cols-2">
        <LogoField label="Logotipo principal" preview="light" name={name} logo={logo} disabled={busy}
          {...field('primary')} />
        <LogoField label="Logotipo para fondo oscuro (opcional)" preview="dark" name={name} logo={logoOnDark}
          disabled={busy || !logo} hint={logo ? undefined : 'Primero sube el logotipo principal.'}
          {...field('on-dark')} />
      </div>
    </div>
  )
}
