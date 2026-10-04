import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import type {
  Bucket,
  Entry,
  EntryPage,
  Recommendation,
  Recommendations,
  Stats,
  User,
  YearInReview,
} from '../api/types';

export const steve: User = { id: 1, username: 'steve' };

export function makeEntry(overrides: Partial<Entry> = {}): Entry {
  return {
    id: 1,
    status: 'IN_PROGRESS',
    rating: null,
    review: null,
    startedOn: null,
    finishedAt: null,
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
    mediaItem: {
      id: 1,
      mediaType: 'TV',
      source: 'TMDB',
      externalId: '95396',
      title: 'Severance',
      releaseYear: 2022,
      overview: 'Work-life balance, taken literally.',
      posterUrl: null,
      backdropUrl: null,
      runtimeMinutes: null,
      seasonCount: 2,
      episodeCount: 19,
      platforms: null,
      genres: ['Drama'],
    },
    ...overrides,
  };
}

export function makePage(items: Entry[]): EntryPage {
  return { items, page: 0, size: 48, totalItems: items.length, totalPages: 1 };
}

export function makeRecommendation(overrides: Partial<Recommendation> = {}): Recommendation {
  return {
    source: 'TMDB',
    mediaType: 'MOVIE',
    externalId: '438631',
    title: 'Dune',
    releaseYear: 2021,
    overview: 'Paul Atreides, a brilliant and gifted young man.',
    posterUrl: null,
    becauseOfTitle: 'Dune: Part Two',
    becauseOfRating: 9,
    seedMatches: 1,
    ...overrides,
  };
}

export function makeRecommendations(overrides: Partial<Recommendations> = {}): Recommendations {
  return {
    mediaType: 'MOVIE',
    items: [makeRecommendation()],
    seedCount: 1,
    ratedCount: 1,
    minimumSeedRating: 7,
    computedAt: new Date().toISOString(),
    fromCache: false,
    ...overrides,
  };
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function months(year: number, counts: Record<number, number> = {}): Bucket[] {
  return MONTHS.map((label, index) => ({
    key: `${year}-${String(index + 1).padStart(2, '0')}`,
    label,
    count: counts[index] ?? 0,
  }));
}

const BY_TYPE: Bucket[] = [
  { key: 'MOVIE', label: 'Films', count: 2 },
  { key: 'TV', label: 'TV', count: 1 },
  { key: 'GAME', label: 'Games', count: 0 },
];

export function makeStats(overrides: Partial<Stats> = {}): Stats {
  return {
    totalItems: 3,
    ratedItems: 2,
    averageRating: 8,
    byMediaType: BY_TYPE,
    byStatus: [
      { key: 'WANT', label: 'Want to start', count: 1 },
      { key: 'IN_PROGRESS', label: 'In progress', count: 0 },
      { key: 'ON_HOLD', label: 'On hold', count: 0 },
      { key: 'COMPLETED', label: 'Finished', count: 2 },
      { key: 'DROPPED', label: 'Gave up', count: 0 },
    ],
    ratingHistogram: Array.from({ length: 10 }, (_, index) => ({
      key: String(index + 1),
      label: String(index + 1),
      count: index === 7 ? 2 : 0,
    })),
    finishedByMonth: months(2026, { 9: 2 }),
    averageRatingByMediaType: [],
    filmTime: { minutes: 293, filmsWithoutRuntime: 0 },
    genres: [
      { name: 'Drama', count: 3, average: 7.5 },
      { name: 'Crime', count: 1, average: null },
    ],
    years: [2026, 2025],
    ...overrides,
  };
}

export function makeYear(overrides: Partial<YearInReview> = {}): YearInReview {
  return {
    year: 2025,
    years: [2026, 2025],
    finished: 3,
    averageRating: 8.7,
    byMediaType: BY_TYPE,
    byMonth: months(2025, { 2: 1, 6: 2 }),
    filmTime: { minutes: 293, filmsWithoutRuntime: 1 },
    genres: [{ name: 'Crime', count: 2, average: 8 }],
    best: [
      { entryId: 7, title: 'Heat', mediaType: 'MOVIE', posterUrl: null, rating: 9 },
      { entryId: 8, title: 'Thief', mediaType: 'MOVIE', posterUrl: null, rating: 7 },
    ],
    ...overrides,
  };
}

/** Remembers the query string of every /api/entries call, for URL-sync assertions. */
export const entriesRequests: string[] = [];

export const server = setupServer(
  http.get('/api/auth/me', () => HttpResponse.json(steve)),
  http.get('/api/auth/options', () => HttpResponse.json({ signupAllowed: true })),
  http.get('/api/entries', ({ request }) => {
    entriesRequests.push(new URL(request.url).search);
    return HttpResponse.json(makePage([makeEntry()]));
  }),
  http.get('/api/recommendations', () => HttpResponse.json(makeRecommendations())),
  http.get('/api/stats', () => HttpResponse.json(makeStats())),
  http.get('/api/stats/years/:year', ({ params }) =>
    HttpResponse.json(makeYear({ year: Number(params.year) })),
  ),
);
