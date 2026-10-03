import { Link } from 'react-router-dom';
import type { Entry, EntryStatus } from '../api/types';
import { STATUS_LABELS, STATUS_ORDER, TYPE_LABELS } from '../labels';
import Poster from './Poster';
import ScoreMeter from './ScoreMeter';

interface Props {
  entry: Entry;
  onStatusChange: (status: EntryStatus) => void;
  priority?: boolean;
}

/**
 * One title in the library.
 *
 * Deliberately not a card: no frame, no shadow, no hover lift. The artwork is
 * the object, and the only bright thing in the grid is the score. Status is an
 * inline select rather than something revealed on hover, so it works the same
 * on a phone, a trackpad and a keyboard.
 */
export default function PosterCard({ entry, onStatusChange, priority }: Props) {
  const { mediaItem: item } = entry;
  const meta = [TYPE_LABELS[item.mediaType], item.releaseYear].filter(Boolean).join(', ');

  return (
    <article className="flex flex-col gap-2">
      <Link
        to={`/item/${entry.id}`}
        className="block aspect-2/3 overflow-hidden rounded-sm bg-surface"
        aria-label={item.title}
      >
        <Poster item={item} priority={priority} sizes="(max-width: 640px) 45vw, 200px" />
      </Link>

      <ScoreMeter rating={entry.rating} />

      <h3 className="text-sm leading-snug font-normal">
        <Link to={`/item/${entry.id}`} className="hover:text-lamp">
          {item.title}
        </Link>
      </h3>

      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-paper-dim">
        <span>{meta}</span>
        <label className="border-l border-edge pl-2">
          <span className="sr-only">Status for {item.title}</span>
          <select
            value={entry.status}
            onChange={(event) => onStatusChange(event.target.value as EntryStatus)}
            className="-ml-1 cursor-pointer appearance-none rounded bg-transparent px-1 py-0.5 hover:bg-surface hover:text-paper"
          >
            {STATUS_ORDER.map((status) => (
              <option key={status} value={status} className="bg-ink text-paper">
                {STATUS_LABELS[status]}
              </option>
            ))}
          </select>
        </label>
      </div>
    </article>
  );
}
