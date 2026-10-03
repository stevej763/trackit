import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useCreateEntry, useSearch } from '../api/hooks';
import type { EntryStatus, MediaType, SearchResult } from '../api/types';
import Button from '../components/Button';
import { STATUS_LABELS, STATUS_ORDER, TYPE_LABELS, TYPE_ORDER } from '../labels';
import { useDebounced } from '../useDebounced';

export default function AddPage() {
  const [type, setType] = useState<MediaType>('MOVIE');
  const [queryText, setQueryText] = useState('');
  const [status, setStatus] = useState<EntryStatus>('WANT');
  const [manualOpen, setManualOpen] = useState(false);

  const query = useDebounced(queryText);
  const search = useSearch(query, type);
  const createEntry = useCreateEntry();

  const [justAdded, setJustAdded] = useState<string | null>(null);

  const add = (result: SearchResult) => {
    createEntry.mutate(
      { mediaType: result.mediaType, status, source: result.source, externalId: result.externalId },
      { onSuccess: () => setJustAdded(result.title) },
    );
  };

  const searchError = search.error instanceof ApiError ? search.error : null;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl">Add to your library</h1>
        <p className="mt-1 text-paper-dim">
          Search for a film, show or game. Artwork and details come along with it.
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex gap-5" role="group" aria-label="What are you adding?">
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
              {TYPE_LABELS[option]}
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

      <label>
        <span className="sr-only">Search for a title</span>
        <input
          type="search"
          value={queryText}
          onChange={(event) => setQueryText(event.target.value)}
          placeholder={`Search for a ${TYPE_LABELS[type].toLowerCase()}`}
          className="field text-lg"
          autoFocus
        />
      </label>

      {justAdded ? (
        <p role="status" className="text-sm text-lamp">
          Added {justAdded} to your library.{' '}
          <Link to="/" className="underline underline-offset-4">
            See your library
          </Link>
        </p>
      ) : null}

      {createEntry.isError ? (
        <p role="alert" className="text-sm text-ember">
          {createEntry.error instanceof Error ? createEntry.error.message : 'Could not add that.'}
        </p>
      ) : null}

      {searchError ? (
        <div role="alert" className="rounded-lg border border-edge bg-surface p-4">
          <p>{searchError.message}</p>
          <button
            type="button"
            onClick={() => setManualOpen(true)}
            className="mt-2 text-lamp underline underline-offset-4"
          >
            Add it by hand instead
          </button>
        </div>
      ) : null}

      {search.isFetching ? <p className="text-paper-dim">Searching...</p> : null}

      {search.data?.length === 0 && query.length >= 2 && !search.isFetching ? (
        <p className="text-paper-dim">
          Nothing found for &ldquo;{query}&rdquo;.{' '}
          <button
            type="button"
            onClick={() => setManualOpen(true)}
            className="text-lamp underline underline-offset-4"
          >
            Add it by hand
          </button>
        </p>
      ) : null}

      {search.data?.length ? (
        <ul className="flex flex-col divide-y divide-edge border-y border-edge">
          {search.data.map((result) => (
            <li key={`${result.source}-${result.externalId}`} className="flex gap-4 py-4">
              <div className="h-24 w-16 shrink-0 overflow-hidden rounded-sm bg-surface">
                {result.posterUrl ? (
                  <img
                    src={result.posterUrl}
                    alt=""
                    loading="lazy"
                    className="h-full w-full object-cover"
                  />
                ) : null}
              </div>

              <div className="min-w-0 flex-1">
                <h2 className="text-base font-normal">
                  {result.title}
                  {result.releaseYear ? (
                    <span className="ml-2 text-paper-dim">{result.releaseYear}</span>
                  ) : null}
                </h2>
                {result.overview ? (
                  <p className="mt-1 line-clamp-2 max-w-[68ch] text-sm text-paper-dim">
                    {result.overview}
                  </p>
                ) : null}
              </div>

              <div className="shrink-0 self-center">
                {result.trackedEntryId ? (
                  <Link
                    to={`/item/${result.trackedEntryId}`}
                    className="text-sm text-paper-dim underline underline-offset-4 hover:text-paper"
                  >
                    Already tracked
                  </Link>
                ) : (
                  <Button onClick={() => add(result)} disabled={createEntry.isPending}>
                    Add
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      ) : null}

      <ManualEntry
        type={type}
        status={status}
        open={manualOpen}
        onToggle={() => setManualOpen((value) => !value)}
        onAdded={(title) => {
          setJustAdded(title);
          setManualOpen(false);
        }}
      />
    </div>
  );
}

function ManualEntry({
  type,
  status,
  open,
  onToggle,
  onAdded,
}: {
  type: MediaType;
  status: EntryStatus;
  open: boolean;
  onToggle: () => void;
  onAdded: (title: string) => void;
}) {
  const createEntry = useCreateEntry();
  const [title, setTitle] = useState('');
  const [year, setYear] = useState('');
  const [posterUrl, setPosterUrl] = useState('');

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    createEntry.mutate(
      {
        mediaType: type,
        status,
        manual: {
          title,
          releaseYear: year ? Number(year) : null,
          posterUrl: posterUrl || null,
        },
      },
      {
        onSuccess: () => {
          onAdded(title);
          setTitle('');
          setYear('');
          setPosterUrl('');
        },
      },
    );
  };

  return (
    <section className="border-t border-edge pt-6">
      <button type="button" onClick={onToggle} aria-expanded={open} className="text-paper-dim hover:text-paper">
        {open ? 'Hide manual entry' : "Can't find it? Add it by hand"}
      </button>

      {open ? (
        <form onSubmit={onSubmit} className="mt-4 flex max-w-xl flex-col gap-4">
          <label className="flex flex-col gap-1.5 text-sm">
            Title
            <input
              className="field"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              required
            />
          </label>

          <div className="flex gap-4">
            <label className="flex w-28 flex-col gap-1.5 text-sm">
              Year
              <input
                className="field"
                type="number"
                min={1850}
                max={2200}
                value={year}
                onChange={(event) => setYear(event.target.value)}
              />
            </label>

            <label className="flex flex-1 flex-col gap-1.5 text-sm">
              Artwork URL
              <input
                className="field"
                type="url"
                value={posterUrl}
                onChange={(event) => setPosterUrl(event.target.value)}
                placeholder="Optional"
              />
            </label>
          </div>

          {createEntry.isError ? (
            <p role="alert" className="text-sm text-ember">
              {createEntry.error instanceof Error ? createEntry.error.message : 'Could not add that.'}
            </p>
          ) : null}

          <Button type="submit" disabled={createEntry.isPending || !title.trim()} className="self-start">
            Add {TYPE_LABELS[type].toLowerCase()}
          </Button>
        </form>
      ) : null}
    </section>
  );
}
