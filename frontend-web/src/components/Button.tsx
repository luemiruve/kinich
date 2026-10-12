import type { ButtonHTMLAttributes } from 'react'

type Variant = 'primary' | 'text'

const variants: Record<Variant, string> = {
  primary:
    'h-12 w-full gap-2 bg-primary-container px-4 text-on-primary hover:bg-primary ' +
    'disabled:cursor-progress disabled:opacity-70 disabled:hover:bg-primary-container',
  text:
    'min-h-11 px-2 text-on-surface underline decoration-secondary underline-offset-4 hover:text-secondary',
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
}

export function Button({ variant = 'primary', className = '', type = 'button', ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={`label-caps inline-flex cursor-pointer items-center justify-center whitespace-nowrap transition-colors duration-200 ${variants[variant]} ${className}`}
      {...props}
    />
  )
}
