import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useEntries, useUpdateStatus } from '../api/hooks';
import type { EntryStatus, MediaType } from '../api/types';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import FilterBar, { type FilterValues } from '../components/FilterBar';
import PosterGrid from '../components/PosterGrid';
import {
  DEFAULT_DIRECTION,
  SORT_OPTIONS,
  STATUS_LABELS,
  STATUS_ORDER,
  TYPE_LABELS_PLURAL,
  TYPE_ORDER,
} from '../labels';
import { useDebounced } from '../useDebounced';

function parseEnum<T extends string>(raw: string | null, allowed: readonly T[]): T | null {
  const upper = raw?.toUpperCase() as T | undefined;
  return upper && allowed.includes(upper) ? upper : null;
}

/** "9 titles", "2 films in progress", "nothing scored 8 and up yet". */
function describeResults(total: number, values: FilterValues): string {
  const noun = values.type ? TYPE_LABELS_PLURAL[values.type].toLowerCase() : 'titles';
  const parts = [`${total} ${total === 1 && !values.type ? 'title' : noun}`];
  if (values.status) {
    parts.push(STATUS_LABELS[values.status].toLowerCase());
  }
  if (values.minRating) {
    parts.push(`scored ${values.minRating} and up`);
  }
  if (values.q) {
    parts.push(`matching "${values.q}"`);
  }
  return parts.join(' ');
}

export default function LibraryPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const sort = SORT_OPTIONS.find((option) => option.value === searchParams.get('sort'))?.value ?? 'added';
  const minRatingParam = Number(searchParams.get('minRating'));

  const values: FilterValues = {
    type: parseEnum<MediaType>(searchParams.get('type'), TYPE_ORDER),
    status: parseEnum<EntryStatus>(searchParams.get('status'), STATUS_ORDER),
    minRating: minRatingParam >= 1 && minRatingParam <= 10 ? minRatingParam : null,
    q: searchParams.get('q') ?? '',
    sort,
    direction: searchParams.get('dir') === 'asc' ? 'asc' : DEFAULT_DIRECTION[sort],
  };

  const page = Math.max(Number(searchParams.get('page') ?? 0) || 0, 0);

  // The search box is local so typing stays responsive; the URL (and the query)
  // catch up once the typing stops, which keeps views linkable either way.
  const [queryText, setQueryText] = useState(values.q);
  const debouncedQuery = useDebounced(queryText);

  useEffect(() => {
    if (debouncedQuery === values.q) {
      return;
    }
    setSearchParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        if (debouncedQuery) {
          next.set('q', debouncedQuery);
        } else {
          next.delete('q');
        }
        next.delete('page');
        return next;
      },
      { replace: true },
    );
  }, [debouncedQuery, values.q, setSearchParams]);

  const update = (changes: Partial<FilterValues>) => {
    if (changes.q !== undefined) {
      setQueryText(changes.q);
      return;
    }
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      // Changing any filter puts you back on the first page.
      next.delete('page');

      if ('type' in changes) {
        if (changes.type) {
          next.set('type', changes.type.toLowerCase());
        } else {
          next.delete('type');
        }
      }
      if ('status' in changes) {
        if (changes.status) {
          next.set('status', changes.status.toLowerCase());
        } else {
          next.delete('status');
        }
      }
      if ('minRating' in changes) {
        if (changes.minRating) {
          next.set('minRating', String(changes.minRating));
        } else {
          next.delete('minRating');
        }
      }
      if (changes.sort) {
        next.set('sort', changes.sort);
        // Each sort has an obvious starting direction: titles from A, scores from 10.
        next.set('dir', DEFAULT_DIRECTION[changes.sort]);
      }
      if (changes.direction) {
        next.set('dir', changes.direction);
      }
      return next;
    });
  };

  const goToPage = (nextPage: number) => {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      next.set('page', String(nextPage));
      return next;
    });
    window.scrollTo({ top: 0 });
  };

  const { data, isPending, isError, error } = useEntries({ ...values, page });
  const updateStatus = useUpdateStatus();

  const hasFilters = Boolean(values.type || values.status || values.minRating || values.q);

  return (
    <div className="flex flex-col gap-6">
      <FilterBar values={{ ...values, q: queryText }} onChange={update} />

      {isError ? (
        <p role="alert" className="text-ember">
          {error instanceof Error ? error.message : 'Could not load your library.'}
        </p>
      ) : null}

      {isPending ? (
        <p className="text-paper-dim" aria-busy="true">
          Loading your library...
        </p>
      ) : null}

      {data ? (
        <>
          <p className="text-sm text-paper-dim" aria-live="polite">
            {describeResults(data.totalItems, values)}
          </p>

          {data.items.length === 0 ? (
            hasFilters ? (
              <EmptyState heading="Nothing matches those filters">
                Try a different status or clear the search to see everything again.
              </EmptyState>
            ) : (
              <EmptyState
                heading="Your library is empty"
                action={
                  <Link
                    to="/add"
                    className="inline-block rounded-md bg-lamp px-4 py-2 text-ink hover:bg-[#f7b641]"
                  >
                    Add your first title
                  </Link>
                }
              >
                Search for a film, show or game and it lands here with its artwork, ready to score.
              </EmptyState>
            )
          ) : (
            <PosterGrid
              entries={data.items}
              onStatusChange={(id, status) => updateStatus.mutate({ id, status })}
            />
          )}

          {data.totalPages > 1 ? (
            <nav className="flex items-center justify-between border-t border-edge pt-4" aria-label="Pages">
              <Button variant="quiet" disabled={page === 0} onClick={() => goToPage(page - 1)}>
                Previous
              </Button>
              <span className="text-sm text-paper-dim">
                Page {data.page + 1} of {data.totalPages}
              </span>
              <Button
                variant="quiet"
                disabled={page >= data.totalPages - 1}
                onClick={() => goToPage(page + 1)}
              >
                Next
              </Button>
            </nav>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
