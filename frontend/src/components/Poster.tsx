import { useState } from 'react';
import type { MediaItem } from '../api/types';

/** Stable hue per title, so a poster-less entry still looks like itself. */
function hueOf(title: string): number {
  let hash = 0;
  for (let index = 0; index < title.length; index += 1) {
    hash = (hash * 31 + title.charCodeAt(index)) % 360;
  }
  return hash;
}

function initials(title: string): string {
  return title
    .split(/\s+/)
    .filter((word) => /[a-z0-9]/i.test(word))
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? '')
    .join('');
}

interface Props {
  item: Pick<MediaItem, 'title' | 'posterUrl'>;
  /** Covers the whole poster area; the caller sets the aspect ratio. */
  sizes?: string;
  priority?: boolean;
}

/**
 * Artwork, or a stand-in built from the title when there is none (manual
 * entries, and the odd provider record with no image). The stand-in is quiet on
 * purpose: it should not compete with real posters in the same grid.
 */
export default function Poster({ item, sizes, priority = false }: Props) {
  const [failed, setFailed] = useState(false);
  const hue = hueOf(item.title);

  if (!item.posterUrl || failed) {
    return (
      <div
        className="flex h-full w-full items-end bg-surface p-3"
        style={{
          backgroundImage: `linear-gradient(160deg, hsl(${hue} 22% 22%), var(--color-ink-deep))`,
        }}
      >
        <span className="font-display text-2xl text-paper-dim" aria-hidden="true">
          {initials(item.title)}
        </span>
      </div>
    );
  }

  return (
    <img
      src={item.posterUrl}
      alt={`${item.title} artwork`}
      sizes={sizes}
      loading={priority ? 'eager' : 'lazy'}
      decoding="async"
      onError={() => setFailed(true)}
      className="h-full w-full object-cover"
    />
  );
}
