import type { Bucket } from '../api/types';

interface Props {
  buckets: Bucket[];
  /** Describes one bar for assistive tech and the hover label, e.g. "scored 7". */
  describe: (bucket: Bucket) => string;
}

/**
 * Vertical bars for an ordered scale (a 1-10 histogram, or months in sequence).
 *
 * Only the tallest bar carries a printed number; every bar reveals its value on
 * hover or keyboard focus, and the table underneath the chart carries all of
 * them for anyone who can't use either.
 */
export default function Columns({ buckets, describe }: Props) {
  const max = Math.max(...buckets.map((bucket) => bucket.count), 1);
  const peak = buckets.reduce((best, bucket) => (bucket.count > best.count ? bucket : best), buckets[0]);

  return (
    <div className="flex h-40 items-end gap-0.5">
      {buckets.map((bucket) => {
        const isPeak = bucket.count > 0 && bucket.key === peak.key;
        return (
          <div key={bucket.key} className="group relative flex h-full flex-1 flex-col justify-end">
            <span
              className={`absolute inset-x-0 -top-0.5 text-center text-xs tabular-nums ${
                isPeak ? 'text-paper' : 'text-paper opacity-0 group-focus-within:opacity-100 group-hover:opacity-100'
              }`}
              aria-hidden="true"
            >
              {bucket.count}
            </span>
            <button
              type="button"
              tabIndex={0}
              aria-label={`${describe(bucket)}: ${bucket.count}`}
              className="w-full rounded-t-sm bg-lamp"
              style={{
                // A floor of 2px keeps empty buckets legible as "zero" rather
                // than as a gap in the axis.
                height: bucket.count === 0 ? '2px' : `${Math.max((bucket.count / max) * 100, 4)}%`,
                opacity: bucket.count === 0 ? 0.25 : 1,
              }}
            />
            <span className="mt-1.5 truncate text-center text-[0.6875rem] text-paper-dim">
              {bucket.label}
            </span>
          </div>
        );
      })}
    </div>
  );
}
