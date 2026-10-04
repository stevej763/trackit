import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useDeleteEntry, useEntry, useUpdateEntry } from '../api/hooks';
import type { Entry, EntryStatus, UpdateEntryPayload } from '../api/types';
import Button from '../components/Button';
import Poster from '../components/Poster';
import ScoreInput from '../components/ScoreInput';
import { STATUS_LABELS, STATUS_ORDER, TYPE_LABELS, describeMediaItem } from '../labels';

interface Draft {
  status: EntryStatus;
  rating: number | null;
  review: string;
  startedOn: string;
  /** The local calendar day of finishedAt, as the date input wants it. */
  finishedOn: string;
}

const pad = (value: number) => String(value).padStart(2, '0');

/** The day an instant falls on here, as YYYY-MM-DD. */
function localDay(instant: string | null): string {
  if (!instant) {
    return '';
  }
  const date = new Date(instant);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/**
 * The instant to store for a finish day. An unchanged day keeps its exact
 * moment; a newly picked one becomes midday local time, which stays on the
 * same day if it's later viewed a few zones away.
 */
function finishedAtFor(day: string, entry: Entry): string | null {
  if (!day) {
    return null;
  }
  if (day === localDay(entry.finishedAt)) {
    return entry.finishedAt;
  }
  const [year, month, date] = day.split('-').map(Number);
  return new Date(year, month - 1, date, 12).toISOString();
}

const draftOf = (entry: Entry): Draft => ({
  status: entry.status,
  rating: entry.rating,
  review: entry.review ?? '',
  startedOn: entry.startedOn ?? '',
  finishedOn: localDay(entry.finishedAt),
});

export default function ItemPage() {
  const { id } = useParams();
  const entryId = Number(id);
  const navigate = useNavigate();

  const { data: entry, isPending, isError, error } = useEntry(entryId);
  // Held here rather than in VerdictForm, which remounts on every save and
  // would lose the mutation's success along with it.
  const updateEntry = useUpdateEntry(entryId);
  const deleteEntry = useDeleteEntry();
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  if (isPending) {
    return (
      <p className="text-paper-dim" aria-busy="true">
        Loading...
      </p>
    );
  }

  if (isError || !entry) {
    const missing = error instanceof ApiError && error.status === 404;
    return (
      <div>
        <p role="alert">
          {missing ? (
            <>That title isn&rsquo;t in your library.</>
          ) : (
            <>Couldn&rsquo;t load that title. {error instanceof Error ? error.message : null}</>
          )}
        </p>
        <Link to="/" className="mt-4 inline-block text-lamp underline underline-offset-4">
          Back to your library
        </Link>
      </div>
    );
  }

  const item = entry.mediaItem;

  return (
    <div className="flex flex-col gap-8">
      <Link to="/" className="text-sm text-paper-dim hover:text-paper">
        Back to your library
      </Link>

      {/* The backdrop is atmosphere, not information: dimmed hard so the title
          and the score stay the brightest things on the page. */}
      <header className="relative isolate overflow-hidden rounded-lg bg-ink-deep">
        {item.backdropUrl ? (
          <img
            src={item.backdropUrl}
            alt=""
            className="absolute inset-0 -z-10 h-full w-full object-cover opacity-25"
          />
        ) : null}
        <div className="absolute inset-0 -z-10 bg-linear-to-t from-ink-deep via-ink-deep/80 to-transparent" />

        <div className="flex flex-col gap-6 p-6 sm:flex-row sm:items-end sm:p-8">
          <div className="w-32 shrink-0 overflow-hidden rounded-sm bg-surface shadow-lg sm:w-40">
            <div className="aspect-2/3">
              <Poster item={item} priority sizes="160px" />
            </div>
          </div>

          <div className="min-w-0">
            <h1 className="text-3xl sm:text-4xl">{item.title}</h1>
            <p className="mt-2 text-paper-dim">{describeMediaItem(item)}</p>
            {item.genres?.length ? (
              <p className="mt-1 text-sm text-paper-dim">{item.genres.join(', ')}</p>
            ) : null}
          </div>
        </div>
      </header>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_20rem]">
        {/* Keyed on the record's own timestamp: a successful save (or a change
            made elsewhere) remounts the form with the stored values, which
            keeps the draft in step without an effect that re-renders. */}
        <VerdictForm key={`${entry.id}:${entry.updatedAt}`} entry={entry} updateEntry={updateEntry} />

        <div className="order-1 flex flex-col gap-6 lg:order-2">
          {item.overview ? (
            <section>
              <h2 className="text-lg">What it&rsquo;s about</h2>
              <p className="mt-2 max-w-[68ch] leading-relaxed text-paper-dim">{item.overview}</p>
            </section>
          ) : null}

          <section className="border-t border-edge pt-4">
            {confirmingDelete ? (
              <div>
                <p className="text-sm">
                  Remove {item.title} from your library? Your score and review go with it.
                </p>
                <div className="mt-3 flex gap-3">
                  <Button
                    variant="danger"
                    onClick={() => deleteEntry.mutate(entry.id, {
                      onSuccess: () => navigate('/', { replace: true }),
                    })}
                    disabled={deleteEntry.isPending}
                  >
                    {deleteEntry.isPending ? 'Removing...' : 'Remove it'}
                  </Button>
                  <Button variant="quiet" onClick={() => setConfirmingDelete(false)}>
                    Keep it
                  </Button>
                </div>
              </div>
            ) : (
              <Button variant="danger" className="-ml-4" onClick={() => setConfirmingDelete(true)}>
                Remove from library
              </Button>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

function VerdictForm({
  entry,
  updateEntry,
}: {
  entry: Entry;
  updateEntry: ReturnType<typeof useUpdateEntry>;
}) {
  const stored = draftOf(entry);
  const [draft, setDraft] = useState<Draft>(stored);
  // Only this version was saved here; a change made elsewhere since isn't.
  const saved = updateEntry.data;
  const justSaved = updateEntry.isSuccess && !!saved && saved.updatedAt === entry.updatedAt;

  const item = entry.mediaItem;
  const dirty = JSON.stringify(draft) !== JSON.stringify(stored);

  const change = (changes: Partial<Draft>) => setDraft((previous) => ({ ...previous, ...changes }));

  const save = (event: React.FormEvent) => {
    event.preventDefault();
    const payload: UpdateEntryPayload = {
      status: draft.status,
      rating: draft.rating,
      review: draft.review.trim() || null,
      startedOn: draft.startedOn || null,
      finishedAt: finishedAtFor(draft.finishedOn, entry),
    };
    updateEntry.mutate(payload);
  };

  return (
    <form
      onSubmit={save}
      className="order-2 flex flex-col gap-6 rounded-lg border border-edge p-5 sm:p-6 lg:order-1"
    >
      <h2 className="text-xl">Your verdict</h2>

      <ScoreInput value={draft.rating} onChange={(rating) => change({ rating })} />

      <label className="flex flex-col gap-1.5 text-sm">
        Review
        <textarea
          className="field min-h-32 leading-relaxed"
          value={draft.review}
          onChange={(event) => change({ review: event.target.value })}
          placeholder={`What did you make of this ${TYPE_LABELS[item.mediaType].toLowerCase()}?`}
        />
      </label>

      <div className="flex flex-wrap gap-4">
        <label className="flex flex-col gap-1.5 text-sm">
          Status
          <select
            className="field w-auto"
            value={draft.status}
            onChange={(event) => change({ status: event.target.value as EntryStatus })}
          >
            {STATUS_ORDER.map((status) => (
              <option key={status} value={status}>
                {STATUS_LABELS[status]}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1.5 text-sm">
          Started
          <input
            className="field w-auto"
            type="date"
            value={draft.startedOn}
            onChange={(event) => change({ startedOn: event.target.value })}
          />
        </label>

        <label className="flex flex-col gap-1.5 text-sm">
          Finished
          <input
            className="field w-auto"
            type="date"
            value={draft.finishedOn}
            onChange={(event) => change({ finishedOn: event.target.value })}
          />
        </label>
      </div>

      {updateEntry.isError ? (
        <p role="alert" className="text-sm text-ember">
          {updateEntry.error instanceof Error
            ? updateEntry.error.message
            : 'Could not save your changes.'}
        </p>
      ) : null}

      <div className="flex items-center gap-4">
        <Button type="submit" disabled={!dirty || updateEntry.isPending}>
          {updateEntry.isPending ? 'Saving...' : 'Save changes'}
        </Button>
        <span aria-live="polite" className="text-sm text-paper-dim">
          {dirty ? 'Unsaved changes' : justSaved ? 'Saved.' : ''}
        </span>
      </div>
    </form>
  );
}
