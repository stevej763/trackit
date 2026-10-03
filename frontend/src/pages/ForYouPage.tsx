import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useCreateEntry, useRecommendations } from '../api/hooks';
import type { EntryStatus, MediaType, Recommendation } from '../api/types';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import TitleRow from '../components/TitleRow';
import {
  STATUS_LABELS,
  STATUS_ORDER,
  TYPE_LABELS,
  TYPE_LABELS_PLURAL,
  TYPE_ORDER,
  formatRelative,
} from '../labels';

/**
 * Suggestions built from the titles you scored highly.
 *
 * The "what is like what" judgement is TMDB's and IGDB's; what this page adds is
 * saying out loud why each thing is here, because a recommendation you can't
 * account for is just noise.
 */
export default function ForYouPage() {
  const [type, setType] = useState<MediaType>('MOVIE');
  const [status, setStatus] = useState<EntryStatus>('WANT');
  const [added, setAdded] = useState<string[]>([]);

  const { data, isPending, isFetching, error, refetch } = useRecommendations(type);
  const createEntry = useCreateEntry();

  const add = (recommendation: Recommendation) => {
    createEntry.mutate(
      {
        mediaType: recommendation.mediaType,
        status,
        source: recommendation.source,
        externalId: recommendation.externalId,
      },
      { onSuccess: () => setAdded((previous) => [...previous, recommendation.externalId]) },
    );
  };

  const providerError = error instanceof ApiError ? error : null;
  const plural = TYPE_LABELS_PLURAL[type].toLowerCase();

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl">For you</h1>
        <p className="mt-1 text-paper-dim">
          {data
            ? `Built from the ${plural} you scored ${data.minimumSeedRating} and up.`
            : 'Built from the titles you scored highest.'}
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex gap-5" role="group" aria-label="Media type">
          {TYPE_ORDER.map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={type === option}
              onClick={() => setType(option)}
              className={`font-display border-b-2 pb-1 text-xl ${
                type === option
                  ? 'border-lamp text-paper'
                  : 'border-transparent text-paper-dim hover:text-paper'
              }`}
            >
              {TYPE_LABELS_PLURAL[option]}
            </button>
          ))}
        </div>

        <label className="ml-auto flex items-center gap-2 text-sm text-paper-dim">
          Add as
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value as EntryStatus)}
            className="field w-auto py-1 text-paper"
          >
            {STATUS_ORDER.map((option) => (
              <option key={option} value={option}>
                {STATUS_LABELS[option]}
              </option>
            ))}
          </select>
        </label>
      </div>

      {providerError ? (
        <div role="alert" className="rounded-lg border border-edge bg-surface p-4">
          <p>{providerError.message}</p>
          <Link to="/add" className="mt-2 inline-block text-lamp underline underline-offset-4">
            Add something by hand instead
          </Link>
        </div>
      ) : null}

      {createEntry.isError ? (
        <p role="alert" className="text-sm text-ember">
          {createEntry.error instanceof Error ? createEntry.error.message : 'Could not add that.'}
        </p>
      ) : null}

      {isPending ? (
        <p className="text-paper-dim" aria-busy="true">
          Looking at what you liked...
        </p>
      ) : null}

      {data ? (
        <>
          {data.ratedCount === 0 ? (
            <EmptyState
              heading="Score a few things first"
              action={
                <Link
                  to="/"
                  className="inline-block rounded-md bg-lamp px-4 py-2 text-ink hover:bg-[#f7b641]"
                >
                  Go to your library
                </Link>
              }
            >
              Suggestions come from what you rated highly, so there needs to be something to go on.
              Score two or three {plural} you love and come back.
            </EmptyState>
          ) : data.seedCount === 0 ? (
            <EmptyState
              heading={`Nothing to match your ${plural} against`}
              action={
                <Link
                  to="/add"
                  className="inline-block rounded-md bg-lamp px-4 py-2 text-ink hover:bg-[#f7b641]"
                >
                  Add something by search
                </Link>
              }
            >
              Suggestions need titles you scored {data.minimumSeedRating} or higher{' '}
              <em>and</em> added by search. Hand-typed entries have nothing for TMDB or IGDB to
              match on.
            </EmptyState>
          ) : data.items.length === 0 ? (
            <EmptyState heading="Nothing new to suggest">
              Everything that came back is already in your library. Score a few more {plural} and
              the suggestions will move.
            </EmptyState>
          ) : (
            <>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-paper-dim" aria-live="polite">
                  {data.items.length} suggestions from {data.seedCount}{' '}
                  {data.seedCount === 1 ? 'title' : 'titles'} you rated highly
                  {data.fromCache ? `, worked out ${formatRelative(data.computedAt)}` : ''}
                </p>
                <Button
                  variant="quiet"
                  onClick={() => void refetch()}
                  disabled={isFetching}
                  className="py-1 text-sm"
                >
                  {isFetching ? 'Refreshing...' : 'Refresh'}
                </Button>
              </div>

              <ul className="flex flex-col divide-y divide-edge border-y border-edge">
                {data.items.map((recommendation) => (
                  <li key={`${recommendation.source}-${recommendation.externalId}`}>
                    <TitleRow
                      title={recommendation.title}
                      releaseYear={recommendation.releaseYear}
                      overview={recommendation.overview}
                      posterUrl={recommendation.posterUrl}
                      note={
                        <>
                          Because you rated{' '}
                          <span className="text-paper">{recommendation.becauseOfTitle}</span>{' '}
                          {recommendation.becauseOfRating}
                          {recommendation.seedMatches > 1
                            ? `, and ${recommendation.seedMatches - 1} more you liked`
                            : ''}
                        </>
                      }
                      action={
                        added.includes(recommendation.externalId) ? (
                          <span className="text-sm text-paper-dim">Added</span>
                        ) : (
                          <Button
                            onClick={() => add(recommendation)}
                            disabled={createEntry.isPending}
                          >
                            Add
                          </Button>
                        )
                      }
                    />
                  </li>
                ))}
              </ul>

              <p className="text-sm text-paper-dim">
                Suggestions come from {type === 'GAME' ? 'IGDB' : 'TMDB'}, based on what people who
                liked your highest-scored {TYPE_LABELS[type].toLowerCase()} titles also liked.
              </p>
            </>
          )}
        </>
      ) : null}
    </div>
  );
}
