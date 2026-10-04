import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import type { Entry, EntryPage, Recommendation, Recommendations, User } from '../api/types';

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

/** Remembers the query string of every /api/entries call, for URL-sync assertions. */
export const entriesRequests: string[] = [];

export const server = setupServer(
  http.get('/api/auth/me', () => HttpResponse.json(steve)),
  http.get('/api/entries', ({ request }) => {
    entriesRequests.push(new URL(request.url).search);
    return HttpResponse.json(makePage([makeEntry()]));
  }),
  http.get('/api/recommendations', () => HttpResponse.json(makeRecommendations())),
);
