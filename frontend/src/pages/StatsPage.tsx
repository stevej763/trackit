import { Link } from 'react-router-dom';
import { useStats } from '../api/hooks';
import type { Bucket } from '../api/types';
import Bars from '../components/Bars';
import Columns from '../components/Columns';
import EmptyState from '../components/EmptyState';
import StatTile from '../components/StatTile';

function Panel({
  heading,
  note,
  children,
}: {
  heading: string;
  note?: string;
  children: React.ReactNode;
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
function ValueTable({
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

export default function StatsPage() {
  const { data: stats, isPending, isError } = useStats();

  if (isPending) {
    return <p className="text-paper-dim" aria-busy="true">Working it out...</p>;
  }

  if (isError || !stats) {
    return (
      <p role="alert" className="text-ember">
        Could not load your stats.
      </p>
    );
  }

  if (stats.totalItems === 0) {
    return (
      <EmptyState
        heading="Nothing to count yet"
        action={
          <Link to="/add" className="inline-block rounded-md bg-lamp px-4 py-2 text-ink hover:bg-[#f7b641]">
            Add your first title
          </Link>
        }
      >
        Once you&rsquo;ve added and scored a few things, this page shows what you actually
        watch and play, and how generous you are with a 10.
      </EmptyState>
    );
  }

  const finishedThisYear = stats.finishedByMonth.reduce((total, month) => total + month.count, 0);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl">Stats</h1>
        <p className="mt-1 text-paper-dim">Your library, counted up.</p>
      </div>

      <section className="grid grid-cols-2 gap-6 rounded-lg border border-edge p-5 sm:grid-cols-4 sm:p-6">
        <StatTile value={stats.totalItems} label="titles tracked" />
        <StatTile value={stats.ratedItems} label="scored" />
        <StatTile value={stats.averageRating ?? '--'} label="average score" />
        <StatTile value={finishedThisYear} label="finished in 12 months" />
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <Panel heading="What you track" note="Across films, TV and games.">
          <Bars buckets={stats.byMediaType} />
        </Panel>

        <Panel heading="Where everything sits" note="Your queue, by status.">
          <Bars buckets={stats.byStatus} />
        </Panel>
      </div>

      <Panel
        heading="How you score"
        note={
          stats.ratedItems === 0
            ? 'Nothing scored yet - score something and its shape shows up here.'
            : 'Every score you have given, 1 to 10.'
        }
      >
        <Columns buckets={stats.ratingHistogram} describe={(bucket) => `Scored ${bucket.key}`} />
        <ValueTable caption="See the numbers" rowHeading="Score" buckets={stats.ratingHistogram} />
      </Panel>

      <Panel heading="What you finished" note="The last twelve months.">
        <Columns
          buckets={stats.finishedByMonth}
          describe={(bucket) => `Finished in ${bucket.key}`}
        />
        <ValueTable caption="See the numbers" rowHeading="Month" buckets={stats.finishedByMonth} />
      </Panel>

      {stats.averageRatingByMediaType.length > 1 ? (
        <Panel heading="Where you are most generous" note="Average score by kind of thing.">
          <ul className="flex flex-col gap-3">
            {stats.averageRatingByMediaType.map((average) => (
              <li key={average.key} className="grid grid-cols-[7.5rem_1fr_2.5rem] items-center gap-3">
                <span className="text-sm text-paper-dim">{average.label}</span>
                <span className="h-2 rounded-r-sm bg-edge/40" aria-hidden="true">
                  <span
                    className="block h-2 rounded-r-sm bg-lamp"
                    style={{ width: `${((average.average ?? 0) / 10) * 100}%` }}
                  />
                </span>
                <span className="text-right text-sm tabular-nums">{average.average ?? '--'}</span>
              </li>
            ))}
          </ul>
        </Panel>
      ) : null}
    </div>
  );
}
