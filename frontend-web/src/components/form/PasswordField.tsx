import { useId, useState, type InputHTMLAttributes } from 'react'

import { inputClassName, labelClassName } from './fieldStyles'

interface PasswordFieldProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'className' | 'type'> {
  label: string
  invalid?: boolean
  describedBy?: string
}

/** Campo de contraseña con botón para mostrarla u ocultarla (diseño ocean_iniciar_sesi_n). */
export function PasswordField({ label, invalid, describedBy, ...inputProps }: PasswordFieldProps) {
  const id = useId()
  const [visible, setVisible] = useState(false)

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className={labelClassName}>
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          className={`${inputClassName} pr-14`}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          {...inputProps}
        />
        <button
          type="button"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          aria-pressed={visible}
          className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-outline transition-colors duration-200 hover:text-on-surface"
        >
          {visible ? <EyeOffIcon /> : <EyeIcon />}
        </button>
      </div>
    </div>
  )
}

function EyeIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.5">
      <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.5">
      <path d="M3 3l18 18" />
      <path d="M10.6 5.1A10.4 10.4 0 0 1 12 5c6.4 0 10 7 10 7a17.6 17.6 0 0 1-3.2 4.2M6.6 6.6C3.8 8.4 2 12 2 12s3.6 7 10 7a9.7 9.7 0 0 0 5.4-1.6" />
      <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
    </svg>
  )
}
