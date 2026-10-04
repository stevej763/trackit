import type {
  ApiErrorBody,
  AuthOptions,
  CreateEntryPayload,
  Entry,
  EntryFilters,
  EntryPage,
  EntryStatus,
  ManualDetails,
  MediaType,
  Recommendations,
  SearchResult,
  Stats,
  UpdateEntryPayload,
  User,
  YearInReview,
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

  /**
   * The most specific thing to tell the user: a field's own message when
   * there's exactly one, since "Please check the highlighted fields" means
   * little on a form that doesn't highlight that field.
   */
  get detail(): string {
    const messages = Object.values(this.details ?? {});
    return messages.length === 1 ? messages[0] : this.message;
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

  authOptions: () => request<AuthOptions>('/auth/options'),

  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/account/password', { method: 'PUT', ...json({ currentPassword, newPassword }) }),

  deleteAccount: (password: string) =>
    request<void>('/account', { method: 'DELETE', ...json({ password }) }),

  /** A plain link target: the server sends it as a file download. */
  exportUrl: '/api/account/export',

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

  updateEntryDetails: (id: number, details: ManualDetails) =>
    request<Entry>(`/entries/${id}/details`, { method: 'PUT', ...json(details) }),

  refreshEntryDetails: (id: number) => request<Entry>(`/entries/${id}/refresh`, { method: 'POST' }),

  deleteEntry: (id: number) => request<void>(`/entries/${id}`, { method: 'DELETE' }),

  search: (query: string, type: MediaType) =>
    request<SearchResult[]>(`/search?q=${encodeURIComponent(query)}&type=${type.toLowerCase()}`),

  // The browser's zone decides which month, and which year, each finish falls in.
  stats: () => request<Stats>(`/stats?tz=${browserZone()}`),

  yearInReview: (year: number) => request<YearInReview>(`/stats/years/${year}?tz=${browserZone()}`),

  recommendations: (type: MediaType, refresh = false) =>
    request<Recommendations>(
      `/recommendations?type=${type.toLowerCase()}${refresh ? '&refresh=true' : ''}`,
    ),
};

function browserZone(): string {
  return encodeURIComponent(Intl.DateTimeFormat().resolvedOptions().timeZone);
}
