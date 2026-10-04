import type {
  ApiErrorBody,
  CreateEntryPayload,
  Entry,
  EntryFilters,
  EntryPage,
  EntryStatus,
  MediaType,
  Recommendations,
  SearchResult,
  Stats,
  UpdateEntryPayload,
  User,
} from './types';

/** A failed API call, carrying the server's own message so the UI can show it. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details?: Record<string, string>;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.error;
    this.details = body.details ?? undefined;
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

async function request<T>(path: string, init?: RequestInit, isRetry = false): Promise<T> {
  const method = init?.method ?? 'GET';
  const headers = new Headers(init?.headers);

  if (init?.body) {
    headers.set('Content-Type', 'application/json');
  }
  if (!SAFE_METHODS.has(method)) {
    // Spring writes the CSRF token to a readable cookie; echo it back.
    const token = readCookie('XSRF-TOKEN');
    if (token) {
      headers.set('X-XSRF-TOKEN', token);
    }
  }

  const response = await fetch(`/api${path}`, { ...init, headers, credentials: 'same-origin' });

  if (response.status === 204) {
    return undefined as T;
  }

  const isJson = response.headers.get('content-type')?.includes('application/json');
  const body = isJson ? await response.json() : null;

  if (!response.ok) {
    const error = new ApiError(
      response.status,
      body ?? { error: 'unknown', message: `Request failed (${response.status})` },
    );
    // The server rotates the CSRF token on sign-in and clears it on sign-out.
    // If a write raced one of those, pick up the new token and try once more.
    if (error.code === 'csrf_failed' && !isRetry) {
      await fetch('/api/auth/me', { credentials: 'same-origin' });
      return request<T>(path, init, true);
    }
    throw error;
  }

  return body as T;
}

const json = (body: unknown): RequestInit => ({ body: JSON.stringify(body) });

export const api = {
  me: () => request<User>('/auth/me'),

  login: (username: string, password: string) =>
    request<User>('/auth/login', { method: 'POST', ...json({ username, password }) }),

  register: (username: string, password: string) =>
    request<User>('/auth/register', { method: 'POST', ...json({ username, password }) }),

  logout: () => request<void>('/auth/logout', { method: 'POST' }),

  listEntries: (filters: EntryFilters) => {
    const params = new URLSearchParams({
      sort: filters.sort,
      direction: filters.direction,
      page: String(filters.page),
    });
    if (filters.type) params.set('type', filters.type.toLowerCase());
    if (filters.status) params.set('status', filters.status.toLowerCase());
    if (filters.minRating) params.set('minRating', String(filters.minRating));
    if (filters.q) params.set('q', filters.q);
    return request<EntryPage>(`/entries?${params}`);
  },

  getEntry: (id: number) => request<Entry>(`/entries/${id}`),

  createEntry: (payload: CreateEntryPayload) =>
    request<Entry>('/entries', { method: 'POST', ...json(payload) }),

  updateEntry: (id: number, payload: UpdateEntryPayload) =>
    request<Entry>(`/entries/${id}`, { method: 'PUT', ...json(payload) }),

  updateEntryStatus: (id: number, status: EntryStatus) =>
    request<Entry>(`/entries/${id}/status`, { method: 'PATCH', ...json({ status }) }),

  deleteEntry: (id: number) => request<void>(`/entries/${id}`, { method: 'DELETE' }),

  search: (query: string, type: MediaType) =>
    request<SearchResult[]>(`/search?q=${encodeURIComponent(query)}&type=${type.toLowerCase()}`),

  // The browser's zone decides which month each finish falls in.
  stats: () =>
    request<Stats>(
      `/stats?tz=${encodeURIComponent(Intl.DateTimeFormat().resolvedOptions().timeZone)}`,
    ),

  recommendations: (type: MediaType, refresh = false) =>
    request<Recommendations>(
      `/recommendations?type=${type.toLowerCase()}${refresh ? '&refresh=true' : ''}`,
    ),
};
