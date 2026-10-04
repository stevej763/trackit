export type MediaType = 'MOVIE' | 'TV' | 'GAME';
export type MetadataSource = 'TMDB' | 'IGDB' | 'MANUAL';
export type EntryStatus = 'WANT' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'DROPPED';

export interface User {
  id: number;
  username: string;
}

/** Settings the sign-in page needs before anyone has signed in. */
export interface AuthOptions {
  signupAllowed: boolean;
}

export interface MediaItem {
  id: number;
  mediaType: MediaType;
  source: MetadataSource;
  externalId: string | null;
  title: string;
  releaseYear: number | null;
  overview: string | null;
  posterUrl: string | null;
  backdropUrl: string | null;
  runtimeMinutes: number | null;
  seasonCount: number | null;
  episodeCount: number | null;
  platforms: string[] | null;
  genres: string[] | null;
}

export interface Entry {
  id: number;
  status: EntryStatus;
  rating: number | null;
  review: string | null;
  startedOn: string | null;
  /** An instant (ISO 8601, UTC). Which day that is depends on the viewer's zone. */
  finishedAt: string | null;
  createdAt: string;
  updatedAt: string;
  mediaItem: MediaItem;
}

export interface EntryPage {
  items: Entry[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface SearchResult {
  source: MetadataSource;
  mediaType: MediaType;
  externalId: string;
  title: string;
  releaseYear: number | null;
  overview: string | null;
  posterUrl: string | null;
  /** Set when this title is already in the caller's library. */
  trackedEntryId: number | null;
}

export interface Bucket {
  key: string;
  label: string;
  count: number;
}

export interface TypeAverage {
  key: string;
  label: string;
  average: number | null;
}

export interface Stats {
  totalItems: number;
  ratedItems: number;
  averageRating: number | null;
  byMediaType: Bucket[];
  byStatus: Bucket[];
  ratingHistogram: Bucket[];
  finishedByMonth: Bucket[];
  averageRatingByMediaType: TypeAverage[];
}

export interface Recommendation {
  source: MetadataSource;
  mediaType: MediaType;
  externalId: string;
  title: string;
  releaseYear: number | null;
  overview: string | null;
  posterUrl: string | null;
  /** The best-scored title of yours that pointed at this one. */
  becauseOfTitle: string;
  becauseOfRating: number;
  /** How many of your seeds pointed here. More is a stronger signal. */
  seedMatches: number;
}

export interface Recommendations {
  mediaType: MediaType;
  items: Recommendation[];
  /** Provider-backed, well-scored titles the suggestions were built from. */
  seedCount: number;
  /** Scored titles of this type however they were added. */
  ratedCount: number;
  minimumSeedRating: number;
  computedAt: string;
  fromCache: boolean;
}

export interface ApiErrorBody {
  error: string;
  message: string;
  details?: Record<string, string> | null;
}

export interface EntryFilters {
  type?: MediaType | null;
  status?: EntryStatus | null;
  minRating?: number | null;
  q?: string | null;
  sort: string;
  direction: 'asc' | 'desc';
  page: number;
}

export interface UpdateEntryPayload {
  status: EntryStatus;
  rating: number | null;
  review: string | null;
  startedOn: string | null;
  finishedAt: string | null;
}

/**
 * A hand-typed title's details. When editing, every field is replaced, so the
 * client sends the full set (see ItemPage's DetailsForm).
 */
export interface ManualDetails {
  title: string;
  releaseYear?: number | null;
  overview?: string | null;
  posterUrl?: string | null;
  backdropUrl?: string | null;
  runtimeMinutes?: number | null;
  seasonCount?: number | null;
  episodeCount?: number | null;
  platforms?: string[] | null;
  genres?: string[] | null;
}

export interface CreateEntryPayload {
  mediaType: MediaType;
  status: EntryStatus;
  source?: MetadataSource;
  externalId?: string;
  manual?: ManualDetails;
}
