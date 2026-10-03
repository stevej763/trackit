import { useId, useRef } from 'react';

const NOTCHES = Array.from({ length: 10 }, (_, index) => index + 1);

interface Props {
  value: number | null;
  onChange: (value: number | null) => void;
}

/**
 * The editable score: ten notches you click, plus the number itself.
 *
 * Built as a radio group so a keyboard reaches it with one Tab and then uses
 * the arrow keys, and so screen readers announce it as one control rather than
 * ten buttons. Clicking the current score clears it - there is no separate
 * "clear" button to find.
 */
export default function ScoreInput({ value, onChange }: Props) {
  const labelId = useId();
  const groupRef = useRef<HTMLDivElement>(null);

  const focusNotch = (notch: number) => {
    groupRef.current
      ?.querySelector<HTMLButtonElement>(`[data-notch="${notch}"]`)
      ?.focus();
  };

  const move = (next: number | null) => {
    onChange(next);
    if (next) {
      focusNotch(next);
    }
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    const current = value ?? 0;
    switch (event.key) {
      case 'ArrowRight':
      case 'ArrowUp':
        event.preventDefault();
        move(Math.min(current + 1, 10));
        break;
      case 'ArrowLeft':
      case 'ArrowDown':
        event.preventDefault();
        move(current <= 1 ? null : current - 1);
        break;
      case 'Home':
        event.preventDefault();
        move(1);
        break;
      case 'End':
        event.preventDefault();
        move(10);
        break;
      case 'Backspace':
      case 'Delete':
        event.preventDefault();
        onChange(null);
        break;
      default:
        break;
    }
  };

  return (
    <div className="flex items-end gap-5">
      <div className="min-w-16">
        <div
          className="font-display text-5xl leading-none tabular-nums"
          style={{ color: value ? 'var(--color-lamp)' : 'var(--color-paper-dim)' }}
          aria-hidden="true"
        >
          {value ?? '--'}
        </div>
        <div className="mt-1 text-xs text-paper-dim" aria-hidden="true">
          out of 10
        </div>
      </div>

      <div className="flex-1">
        <div id={labelId} className="mb-2 text-sm text-paper-dim">
          Your score
        </div>
        <div
          ref={groupRef}
          role="radiogroup"
          aria-labelledby={labelId}
          onKeyDown={onKeyDown}
          className="flex gap-0.5"
        >
          {NOTCHES.map((notch) => {
            const filled = value !== null && notch <= value;
            const isCurrent = value === notch;
            return (
              <button
                key={notch}
                type="button"
                data-notch={notch}
                role="radio"
                aria-checked={isCurrent}
                aria-label={`${notch} out of 10`}
                tabIndex={isCurrent || (value === null && notch === 1) ? 0 : -1}
                onClick={() => onChange(isCurrent ? null : notch)}
                className={`h-9 flex-1 rounded-sm border-0 p-0 ${
                  filled ? 'bg-lamp' : 'bg-edge hover:bg-edge-bright'
                }`}
              />
            );
          })}
        </div>
        <p className="mt-2 text-xs text-paper-dim">
          {value ? 'Click the lit notch again to clear it.' : 'Click a notch to score this.'}
        </p>
      </div>
    </div>
  );
}
