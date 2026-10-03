import type { EntryStatus, MediaType } from './api/types';

/*
 * One vocabulary for the whole app. An action keeps the same name from the
 * button that triggers it to the filter that finds it afterwards, so "Finished"
 * is never "Completed" on the next screen.
 */

export const STATUS_LABELS: Record<EntryStatus, string> = {
  WANT: 'Want to start',
  IN_PROGRESS: 'In progress',
  ON_HOLD: 'On hold',
  COMPLETED: 'Finished',
  DROPPED: 'Gave up',
};

export const STATUS_ORDER: EntryStatus[] = [
  'WANT',
  'IN_PROGRESS',
  'ON_HOLD',
  'COMPLETED',
  'DROPPED',
];

export const TYPE_LABELS: Record<MediaType, string> = {
  MOVIE: 'Film',
  TV: 'TV',
  GAME: 'Game',
};

export const TYPE_LABELS_PLURAL: Record<MediaType, string> = {
  MOVIE: 'Films',
  TV: 'TV',
  GAME: 'Games',
};

export const TYPE_ORDER: MediaType[] = ['MOVIE', 'TV', 'GAME'];

export const SORT_OPTIONS = [
  { value: 'added', label: 'Recently added' },
  { value: 'title', label: 'Title' },
  { value: 'rating', label: 'Score' },
  { value: 'finished', label: 'Recently finished' },
  { value: 'year', label: 'Release year' },
] as const;

/** Sensible direction for each sort, so "Title" starts at A and "Score" at 10. */
export const DEFAULT_DIRECTION: Record<string, 'asc' | 'desc'> = {
  added: 'desc',
  title: 'asc',
  rating: 'desc',
  finished: 'desc',
  year: 'desc',
};

/** "2 seasons", "166 minutes", "PC, PS5" - whatever this kind of thing has. */
export function describeMediaItem(item: {
  mediaType: MediaType;
  releaseYear: number | null;
  runtimeMinutes: number | null;
  seasonCount: number | null;
  platforms: string[] | null;
}): string {
  const parts: string[] = [];
  parts.push(item.releaseYear ? `${item.releaseYear} ${TYPE_LABELS[item.mediaType].toLowerCase()}` : TYPE_LABELS[item.mediaType]);

  if (item.runtimeMinutes) {
    parts.push(`${item.runtimeMinutes} minutes`);
  }
  if (item.seasonCount) {
    parts.push(item.seasonCount === 1 ? '1 season' : `${item.seasonCount} seasons`);
  }
  if (item.platforms?.length) {
    parts.push(item.platforms.slice(0, 3).join(', '));
  }
  return parts.join(', ');
}

/** "just now", "3 hours ago", "2 days ago" - for the recommendations timestamp. */
export function formatRelative(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';

  const seconds = Math.round((then - Date.now()) / 1000);
  if (Math.abs(seconds) < 60) return 'just now';

  const formatter = new Intl.RelativeTimeFormat('en-GB', { numeric: 'auto' });
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60],
  ];
  for (const [unit, size] of units) {
    if (Math.abs(seconds) >= size) {
      return formatter.format(Math.round(seconds / size), unit);
    }
  }
  return 'just now';
}

export function formatDate(iso: string | null): string {
  if (!iso) return '';
  return new Date(`${iso}T00:00:00`).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}
