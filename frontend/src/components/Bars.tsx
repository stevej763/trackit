import type { Bucket } from '../api/types';

/**
 * Horizontal bars for a handful of named categories.
 *
 * One series, so no legend: the heading names it. Labels sit outside the bar in
 * text colour rather than on the mark, and the bar itself is thin with a
 * rounded far end anchored to a zero baseline.
 */
export default function Bars({ buckets }: { buckets: Bucket[] }) {
  const max = Math.max(...buckets.map((bucket) => bucket.count), 1);

  return (
    <ul className="flex flex-col gap-3">
      {buckets.map((bucket) => (
        <li key={bucket.key} className="grid grid-cols-[7.5rem_1fr_2.5rem] items-center gap-3">
          <span className="truncate text-sm text-paper-dim">{bucket.label}</span>
          <span className="h-2 rounded-r-sm bg-edge/40" aria-hidden="true">
            <span
              className="block h-2 rounded-r-sm bg-lamp"
              style={{ width: `${(bucket.count / max) * 100}%` }}
            />
          </span>
          <span className="text-right text-sm tabular-nums">{bucket.count}</span>
        </li>
      ))}
    </ul>
  );
}
