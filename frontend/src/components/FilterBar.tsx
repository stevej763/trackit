import type { EntryStatus, MediaType } from '../api/types';
import {
  SORT_OPTIONS,
  STATUS_LABELS,
  STATUS_ORDER,
  TYPE_LABELS_PLURAL,
  TYPE_ORDER,
} from '../labels';

export interface FilterValues {
  type: MediaType | null;
  status: EntryStatus | null;
  minRating: number | null;
  q: string;
  sort: string;
  direction: 'asc' | 'desc';
}

const MIN_RATINGS = [6, 7, 8, 9, 10];

interface Props {
  values: FilterValues;
  onChange: (changes: Partial<FilterValues>) => void;
}

export default function FilterBar({ values, onChange }: Props) {
  return (
    <div className="flex flex-col gap-4">
      {/* The type switch is the primary cut, so it gets the underline and the
          display face; status is a secondary filter and stays as chips. */}
      <div className="flex flex-wrap items-baseline justify-between gap-4">
        <div className="-mb-px flex flex-wrap gap-5" role="group" aria-label="Media type">
          {[null, ...TYPE_ORDER].map((type) => {
            const active = values.type === type;
            return (
              <button
                key={type ?? 'all'}
                type="button"
                aria-pressed={active}
                onClick={() => onChange({ type })}
                className={`font-display text-xl border-b-2 pb-1 ${
                  active
                    ? 'border-lamp text-paper'
                    : 'border-transparent text-paper-dim hover:text-paper'
                }`}
              >
                {type ? TYPE_LABELS_PLURAL[type] : 'Everything'}
              </button>
            );
          })}
        </div>

        <div className="flex items-center gap-2 text-sm text-paper-dim">
          <label className="flex items-center gap-2">
            Sort by
            <select
              value={values.sort}
              onChange={(event) => onChange({ sort: event.target.value })}
              className="field w-auto py-1 text-paper"
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            onClick={() => onChange({ direction: values.direction === 'asc' ? 'desc' : 'asc' })}
            aria-label={
              values.direction === 'asc' ? 'Sorted lowest first. Reverse' : 'Sorted highest first. Reverse'
            }
            className="field w-auto px-2 py-1 hover:text-paper"
          >
            <svg
              viewBox="0 0 16 16"
              width="14"
              height="14"
              aria-hidden="true"
              className={values.direction === 'asc' ? 'rotate-180' : ''}
              fill="none"
              stroke="currentColor"
              strokeWidth="1.6"
            >
              <path d="M8 2.5v11M3.5 9L8 13.5 12.5 9" />
            </svg>
          </button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {[null, ...STATUS_ORDER].map((status) => {
          const active = values.status === status;
          return (
            <button
              key={status ?? 'any'}
              type="button"
              aria-pressed={active}
              onClick={() => onChange({ status })}
              className={`rounded-full px-3 py-1 text-sm ${
                active
                  ? 'bg-paper text-ink'
                  : 'bg-surface text-paper-dim hover:text-paper'
              }`}
            >
              {status ? STATUS_LABELS[status] : 'Any status'}
            </button>
          );
        })}

        <label className="ml-auto flex items-center gap-2 text-sm text-paper-dim">
          Score
          <select
            value={values.minRating ?? ''}
            onChange={(event) =>
              onChange({ minRating: event.target.value ? Number(event.target.value) : null })
            }
            className="field w-auto py-1 text-paper"
          >
            <option value="">Any</option>
            {MIN_RATINGS.map((rating) => (
              <option key={rating} value={rating}>
                {rating === 10 ? '10' : `${rating} and up`}
              </option>
            ))}
          </select>
        </label>

        <label>
          <span className="sr-only">Search your library</span>
          <input
            type="search"
            value={values.q}
            onChange={(event) => onChange({ q: event.target.value })}
            placeholder="Find a title"
            className="field w-48 py-1 text-sm"
          />
        </label>
      </div>
    </div>
  );
}
