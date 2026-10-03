import type { ButtonHTMLAttributes } from 'react';

type Variant = 'primary' | 'quiet' | 'danger';

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-lamp text-ink hover:bg-[#f7b641] disabled:bg-edge disabled:text-paper-dim',
  quiet: 'border border-edge text-paper hover:border-edge-bright disabled:text-paper-dim',
  danger: 'text-ember hover:bg-surface',
};

export default function Button({
  variant = 'primary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      {...props}
      className={`rounded-md px-4 py-2 disabled:cursor-not-allowed ${VARIANTS[variant]} ${className}`}
    />
  );
}
