import type { ReactNode } from 'react';
import type { Bucket } from '../api/types';

export function Panel({
  heading,
  note,
  children,
}: {
  heading: string;
  note?: string;
  children: ReactNode;
}) {
  return (
    <section className="rounded-lg border border-edge p-5 sm:p-6">
      <h2 className="text-lg">{heading}</h2>
      {note ? <p className="mt-1 mb-5 text-sm text-paper-dim">{note}</p> : <div className="mb-5" />}
      {children}
    </section>
  );
}

/** The accessible fallback the charts lean on: every value, in order. */
export function ValueTable({
  caption,
  rowHeading,
  buckets,
}: {
  caption: string;
  rowHeading: string;
  buckets: Bucket[];
}) {
  return (
    <details className="mt-4">
      <summary className="cursor-pointer text-sm text-paper-dim hover:text-paper">
        {caption}
      </summary>
      <div className="mt-3 overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-edge text-paper-dim">
              <th scope="col" className="py-1 text-left font-normal">
                {rowHeading}
              </th>
              <th scope="col" className="py-1 text-right font-normal">
                Titles
              </th>
            </tr>
          </thead>
          <tbody>
            {buckets.map((bucket) => (
              <tr key={bucket.key} className="border-b border-edge/50">
                <th scope="row" className="py-1 text-left font-normal">
                  {bucket.label}
                </th>
                <td className="py-1 text-right tabular-nums">{bucket.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
