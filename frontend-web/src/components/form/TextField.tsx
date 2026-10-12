import { useId, type InputHTMLAttributes } from 'react'

import { inputClassName, labelClassName } from './fieldStyles'

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'className'> {
  label: string
  invalid?: boolean
  describedBy?: string
}

export function TextField({ label, invalid, describedBy, ...inputProps }: TextFieldProps) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className={labelClassName}>
        {label}
      </label>
      <input
        id={id}
        className={inputClassName}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
        {...inputProps}
      />
    </div>
  )
}
