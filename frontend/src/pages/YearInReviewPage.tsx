import { Link, Navigate, useParams } from 'react-router-dom';
import { useYearInReview } from '../api/hooks';
import type { YearHighlight } from '../api/types';
import Bars from '../components/Bars';
import Columns from '../components/Columns';
import EmptyState from '../components/EmptyState';
import FilmTimeTile from '../components/FilmTimeTile';
import GenreTable from '../components/GenreTable';
import Poster from '../components/Poster';
import ScoreMeter from '../components/ScoreMeter';
import { Panel, ValueTable } from '../components/StatPanel';
import StatTile from '../components/StatTile';
import YearLinks from '../components/YearLinks';
import { TYPE_LABELS } from '../labels';

function Best({ highlight }: { highlight: YearHighlight }) {
  return (
    <li className="flex flex-col gap-2">
      <Link
        to={`/item/${highlight.entryId}`}
        className="block aspect-2/3 overflow-hidden rounded-sm bg-surface"
        aria-label={highlight.title}
      >
        <Poster item={highlight} sizes="(max-width: 640px) 30vw, 160px" />
      </Link>
      <ScoreMeter rating={highlight.rating} />
      <div className="text-sm leading-snug">
        <Link to={`/item/${highlight.entryId}`} className="hover:text-lamp">
          {highlight.title}
        </Link>
      </div>
      <div className="text-xs text-paper-dim">{TYPE_LABELS[highlight.mediaType]}</div>
    </li>
  );
}

export default function YearInReviewPage() {
  const { year: param } = useParams();
  const valid = /^\d{4}$/.test(param ?? '');
  const year = valid ? Number(param) : NaN;
  const { data: review, isPending, isError } = useYearInReview(year);

  if (!valid) {
    return <Navigate to="/stats" replace />;
  }

  if (isPending) {
    return <p className="text-paper-dim" aria-busy="true">Working it out...</p>;
  }

  if (isError || !review) {
    return (
      <p role="alert" className="text-ember">
        Could not load {year}.
      </p>
    );
  }

  const header = (
    <div>
      <Link to="/stats" className="text-sm text-paper-dim underline decoration-edge-bright underline-offset-4 hover:text-paper">
        All stats
      </Link>
      <h1 className="mt-3 text-2xl">Your {review.year}</h1>
      <p className="mt-1 text-paper-dim">Everything you finished that year.</p>
      <div className="mt-4">
        <YearLinks years={review.years} current={review.year} />
      </div>
    </div>
  );

  if (review.finished === 0) {
    return (
      <div className="flex flex-col gap-6">
        {header}
        <EmptyState
          heading={`Nothing finished in ${review.year}`}
          action={
            <Link to="/" className="inline-block rounded-md bg-lamp px-4 py-2 text-ink hover:bg-[#f7b641]">
              Go to your library
            </Link>
          }
        >
          Mark a title Finished and it counts towards the year you finished it. You can set the
          date on its page if it was a while ago.
        </EmptyState>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {header}

      <section className="grid grid-cols-2 gap-6 rounded-lg border border-edge p-5 sm:grid-cols-3 sm:p-6">
        <StatTile value={review.finished} label="finished" />
        <StatTile value={review.averageRating ?? '--'} label="average score" />
        <FilmTimeTile filmTime={review.filmTime} />
      </section>

      {review.best.length > 0 ? (
        <Panel heading={`Best of ${review.year}`} note="Your highest scores among what you finished.">
          <ul className="grid grid-cols-3 gap-x-4 gap-y-6 sm:grid-cols-6">
            {review.best.map((highlight) => (
              <Best key={highlight.entryId} highlight={highlight} />
            ))}
          </ul>
        </Panel>
      ) : null}

      <Panel heading="Month by month" note="When you finished things.">
        <Columns
          buckets={review.byMonth}
          describe={(bucket) => `Finished in ${bucket.label} ${review.year}`}
        />
        <ValueTable caption="See the numbers" rowHeading="Month" buckets={review.byMonth} />
      </Panel>

      <div className="grid gap-6 lg:grid-cols-2">
        <Panel heading="What you finished" note="Across films, TV and games.">
          <Bars buckets={review.byMediaType} />
        </Panel>

        {review.genres.length > 0 ? (
          <Panel heading="Your genres that year" note="A title can have several.">
            <GenreTable genres={review.genres} />
          </Panel>
        ) : null}
      </div>
    </div>
  );
}
