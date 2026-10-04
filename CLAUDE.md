# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A personal media tracker: films, TV shows and video games you're watching, playing or want to get
to, each with a status, a score out of 10 and a free-text review. Multi-user with username/password
sign-in and open registration; every account's library, scores and reviews are private to it, while
the catalogue of titles is shared so metadata is fetched once.

Three services, composed: a Spring Boot API, PostgreSQL, and a React SPA served by nginx. A fourth,
`backup`, runs `pg_dump` into `./backups` on an interval (`backup/backup.sh`, restore steps inside).

## Commands

Run everything with Docker (the primary workflow — there is no Java or Maven on the dev machine):

```sh
cp .env.example .env
docker compose up --build
```

Visit `http://localhost:8300` (`TRACKIT_PORT`). Only the `web` service publishes a host port; `api`
is reachable only through nginx's `/api/` proxy, and `db` only from inside the compose network.

```sh
docker compose logs -f api              # backend logs
docker compose run --rm api-test        # backend tests (mounts the docker socket for Testcontainers)
docker compose exec db psql -U trackit -d trackit
docker compose restart api              # sessions survive this; they live in Postgres
```

Backend without Docker needs a JDK 21 (`brew install --cask temurin@21`) and a Postgres on
`localhost:5432`:

```sh
cd backend && ./mvnw spring-boot:run     # :8080
cd backend && ./mvnw test                # needs a running Docker for Testcontainers
```

Frontend without Docker (Node 20.19+):

```sh
cd frontend
npm install
npm run dev        # :5173, proxies /api to localhost:8080 (see vite.config.ts)
npm test           # Vitest, 43 tests
npm run lint
npm run build      # type-checks with tsc --noEmit first, so test files are checked too
```

## Architecture

### One origin, no CORS

nginx serves the SPA and reverse-proxies `/api/` to the `api` service, so the browser only ever
talks to one origin. There is deliberately **no CORS configuration anywhere in this project**, and
the session cookie is first-party. Keep it that way: exposing the API on its own host port would
require CORS and `SameSite=None`, and would make the cookie third-party.

### `backend/` — Spring Boot 3.5, Java 21, Maven

Packages under `dev.stevejones.trackit`, organised by feature rather than by layer:

- **`auth/`** — `AppUser` (username in a `citext` column, BCrypt hash), `AppUserPrincipal` (a
  `UserDetails` carrying the user id so handlers scope queries without a lookup), `AuthController`
  (`/api/auth/register`, `/login`, `/me`, `/options`). Logout is Spring Security's own filter,
  configured in `SecurityConfig`. `LoginThrottle` limits failed sign-ins per username and per
  address, and sign-ups per address, in memory. `AccountController`/`AccountService` change the
  password, export the library and delete the account.
- **`media/`** — `MediaItem`, the shared catalogue row, deduplicated on
  `(source, mediaType, externalId)`.
- **`entry/`** — `Entry`, one user's status/score/review for one `MediaItem`. `EntryService` holds
  the behaviour, `EntrySpecifications` the filtering and ordering.
- **`search/`** — the `MetadataProvider` interface and its two implementations, plus
  `MetadataProviders` which picks one by `MediaType`, and `CatalogueRefresher`, which re-fetches
  catalogue rows on request and in a nightly `@Scheduled` job.
- **`recommend/`** — suggestions built from highly-scored entries, plus the cache table behind them.
- **`stats/`** — native-SQL aggregates for the stats page.
- **`common/`** — `GlobalExceptionHandler` and the single `ApiError` response shape.
- **`config/`** — `SecurityConfig`, `RestClientConfig`, `WebConfig` (case-insensitive enum query
  parameters, so the SPA can use `?type=movie`).

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate's `ddl-auto` is `none`.
**Never edit an applied migration** — add `V5__…sql` instead.

Sessions are stored in Postgres via Spring Session JDBC, so restarting `api` doesn't sign everyone
out. `V2__spring_session.sql` creates those tables and `initialize-schema` is `never`, keeping
Flyway the single owner of the schema. `AppUserPrincipal` is serialised into the session, so
changing its fields invalidates existing sessions.

### CSRF, and the one subtle bit

Auth is a session cookie plus a readable `XSRF-TOKEN` cookie that the SPA echoes in `X-XSRF-TOKEN`
(`config/SpaCsrfTokenRequestHandler`, `config/CsrfCookieFilter` — Spring Security's documented SPA
recipe). The subtlety: signing in **rotates** the CSRF token, and the replacement is deferred — its
cookie is only written if something reads the token's value during that response. `AuthController`
reads it deliberately (`renderRotatedCsrfToken`), and the logout success handler issues a fresh one,
because logout clears the cookie and the very next request is usually a sign-in `POST`. Without
either, a client is left holding a token the server has discarded and its next write gets a 403.
`api/client.ts` also retries once on `csrf_failed` as a backstop. If you touch any of this, test
register → write and logout → sign-in with no request in between.

### Sorting, and why it isn't in the `Pageable`

`EntryController` maps client sort keys (`added`, `title`, `rating`, `finished`, `year`, `updated`)
onto entity paths through a whitelist, then passes the property and direction down. The ordering is
applied inside `EntrySpecifications.withMediaItemOrderedBy`, with an **unsorted** `PageRequest`, for
two reasons: Spring Data's `Sort` cannot express null precedence against a criteria query (it throws
`UnsupportedOperationException`), and doing it by hand lets the `ORDER BY` reuse the media-item fetch
join instead of adding a second one. Nulls are pushed last in both directions with a `CASE WHEN …
IS NULL` expression, so unrated and unfinished entries never crowd the top of a descending list. A
sorted `Pageable` would silently override all of it.

### Metadata providers

`TmdbProvider` handles `MOVIE` and `TV` (note TMDB uses `title`/`release_date` for films but
`name`/`first_air_date` for shows). `IgdbProvider` handles `GAME`, posts IGDB's own query language as
a `text/plain` body, and gets its bearer token from `IgdbTokenStore`, which caches a Twitch
client-credentials token in memory and refreshes it once on a 401. Game ids are checked against
`\d{1,19}` and search terms are stripped of quotes and semicolons before going into a query body.
TMDB ids are checked against `\d{1,10}`, since they become a path segment (`550/credits` would
otherwise file the wrong thing in the shared catalogue).

Credentials stay out of URLs, because a transport failure's message quotes the URL and the cause of
a `ProviderException` is logged. The Twitch secret goes in a form body; `TMDB_API_KEY` may be the
v4 Read Access Token (sent as a Bearer header, and preferred) or the v3 key, which only works in the
query string, so `TmdbProvider` logs the underlying I/O error rather than the exception quoting it.

`recommendationsFor` takes **all** seeds at once, not one at a time: IGDB expands `similar_games`
for a whole set of ids in a single request, while TMDB needs one call per title. A batch signature
lets the efficient provider actually be efficient. For TMDB, a seed that fails (say, a title TMDB has
since removed) is skipped rather than failing the batch; only all of them failing is an error. Note
also that TMDB's recommendation endpoints accept no `include_adult` parameter, unlike search, so
`adult` results are filtered in `TmdbProvider` instead.

Every provider failure becomes a `ProviderException` whose message is written to be shown to the
user as-is ("Set `TMDB_API_KEY`, or add the title by hand") and surfaces as a 503. **With no keys
configured the app must stay fully usable through manual entry** — the providers refuse to make a
request at all rather than failing mid-flight. There are tests for that.

### Recommendations

TrackIt does not compute similarity itself. TMDB and IGDB already derive it from far more behaviour
than a personal library could contain, so `RecommendationService` picks seeds, aggregates what comes
back, and keeps track of *why* each suggestion is there. Four decisions worth keeping:

- **Seeds** are provider-backed entries of one type, scored at least `MINIMUM_SEED_RATING` (7) and
  not given up on, best first, capped at `MAX_SEEDS` (10) because TMDB costs one request per seed.
  Manual entries can never be seeds — there is no id to ask about — which is why the response carries
  both `seedCount` and `ratedCount`: the UI needs to tell "score something first" apart from
  "everything you scored was typed in by hand".
- **Scoring** sums the ratings of the seeds that pointed at a title, so two of your favourites
  agreeing (8 + 7 = 15) beats a single 10. That is the whole ranking idea.
- **Ties break on the provider's own position, then title.** This matters more than it looks: seeds
  share a score often enough that most of a page is one big tie, and both providers return results
  most-relevant-first. An early version broke ties on title alone and produced pages of suggestions
  in alphabetical order, which reads as broken even though the scoring was right.
- **The cache key is a seed fingerprint**, a hash of the seed ids and their scores, alongside a 24h
  TTL. Scoring something new changes the fingerprint and so refreshes suggestions at once rather
  than leaving the user waiting out the TTL. If a provider is down and a cached payload exists, the
  stale list is served rather than an error; an unreadable payload (the record shape changed under
  an old row) is treated as a miss and recomputed, not a 500 on every request. Tracked titles are
  filtered out of every response, cached ones included: adding something without scoring it leaves
  the fingerprint alone, so the cached list can still contain it.

### Provider calls never hold a database connection

A provider call can take seconds (10 s read timeout), and the Hikari pool has 10 connections, so no
code path calls TMDB or IGDB inside a transaction. `RecommendationService.forUser` reads a snapshot
in one short read-only transaction, asks the providers with nothing open, and stores the result in a
second short one, re-reading the cache row rather than trusting the detached one. `EntryService.create`
and `refreshDetails` are likewise not `@Transactional`. `TmdbProvider.recommendationsFor` fetches
seeds in parallel on virtual threads with an overall deadline (`RECOMMENDATIONS_DEADLINE`, 15 s); a
seed still outstanding then is skipped like a failed one, and the executor is `shutdownNow()`ed, not
closed, because `close()` would wait the stragglers out.

### Sign-in throttling and sign-up

`LoginThrottle` counts failures in a fixed window per username (5 / 15 min, case-insensitive) and per
address (20 / 15 min), and sign-ups per address (5 / hour). It throttles rather than locks out, so
someone guessing at your username can delay you but not lock you out; a success clears the
username's count but never the address's. Confirming a password on the account page goes through the
same counts. The address is `getRemoteAddr()`, which Spring takes from the left-most
`X-Forwarded-For`, so nginx **overwrites** that header with `$remote_addr` (and blanks `Forwarded`)
rather than appending; otherwise a client could pick its own address. Behind another TLS proxy, use
nginx's realip module. Counts live in memory and reset on restart, which is fine for one instance.
Tests share an application context, so anything that signs in calls `throttle.clear()` first.

`TRACKIT_ALLOW_SIGNUP=false` makes `/auth/register` a 403 `signup_closed`; `/auth/options` (public)
tells the SPA, which then drops the "Create one" link.

### API shape

All under `/api`, all JSON, all errors as `{error, message, details?}`.

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/auth/register` | Signs the new account straight in. 409 on a duplicate (case-insensitive), 403 `signup_closed` when sign-up is off, 429 when throttled. |
| `POST` | `/auth/login`, `/auth/logout` | Login answers 429 with `Retry-After` when throttled. |
| `GET` | `/auth/options` | Public. `{signupAllowed}`. |
| `PUT` | `/account/password` | `{currentPassword, newPassword}`. Signs out every other session of the user. |
| `GET` | `/account/export` | The whole library as a JSON download (`format: 1`). |
| `DELETE` | `/account` | `{password}`. Deletes the user, their entries and hand-typed titles, ends every session, and issues a fresh CSRF token like logout. |
| `GET` | `/auth/me` | 401 when anonymous — the SPA uses this on boot. |
| `GET` | `/search?q=&type=movie\|tv\|game` | Provider search. Persists nothing; flags hits already in your library. |
| `POST` | `/entries` | Either `{source, externalId}` or `{manual: {...}}`. |
| `GET` | `/entries` | `type`, `status`, `minRating`, `q`, `sort`, `direction`, `page`, `size`. |
| `GET`/`PUT`/`DELETE` | `/entries/{id}` | |
| `PATCH` | `/entries/{id}/status` | The grid's quick status change. |
| `PUT` | `/entries/{id}/details` | Hand-typed titles only (400 otherwise). Replaces every field, like `PUT /entries/{id}`. |
| `POST` | `/entries/{id}/refresh` | Provider titles only. Re-fetches the shared row unless it was fetched in the last 10 minutes. |
| `GET` | `/recommendations?type=&refresh=` | Built from your best-scored titles. Cached per user and type. |
| `GET` | `/stats?tz=` | Gap-filled buckets. `tz` is the browser's IANA zone (UTC if absent), used to bucket finishes by month. |

`PUT /entries/{id}` replaces **every** editable field — a `null` clears it. There is no partial
patch, so the client always sends the whole editable set; `PATCH /entries/{id}/status` exists for the
one case that can't. One exception to "null clears": moving an entry *into* `COMPLETED` with no
`finishedAt` records the current moment, since the stats page counts completions by month and a
finished entry with no date would quietly go missing. Clearing the date on an already-finished entry
still clears it.

`finishedAt` is a `timestamptz` (an instant), not a date: "today" on the server's UTC clock was the
wrong day for anyone finishing something late in the evening. Which day an instant falls on is
decided where it's shown: `ItemPage` converts to the local day for its date input (a newly picked
day is stored as local midday; an untouched one keeps its exact moment), and `/stats` buckets in the
`tz` the SPA sends. Only region ids are accepted for `tz`: Java takes `+05:00` too, but Postgres
reads offsets with POSIX's inverted sign. `startedOn` is still a plain date, since the server never
fills it in. `V4` converted existing dates to midday UTC, which is the same day from UTC-12 to UTC+11.

Another user's entry id returns **404, not 403**, so ids can't be enumerated.

Hand-typed artwork URLs must be `http(s)` (`EntryDtos.WEB_ADDRESS`): they go straight into an
`<img src>`. Provider titles can't be edited, since everyone tracking them shares the row; they
refresh instead. The nightly refresh (`TRACKIT_METADATA_REFRESH_CRON`, `-` to disable) takes up to 50
tracked provider rows older than a week that are TV, undated, or released since last year. It gives
up on a media type after three failures in a row (no key, provider down) without stopping the other,
and a title the provider no longer has (`UnknownTitleException`) is marked as checked so it can't
sit at the head of the queue every night.

`EntryService.create` is deliberately **not** `@Transactional`. Postgres aborts a transaction on a
constraint violation, so recovering from a lost insert race (a double-clicked Add) only works if the
catalogue insert and the entry insert each run in their own transaction; a lost entry race becomes a
409. `GlobalExceptionHandler` also maps any other `DataIntegrityViolationException` to 409 as a
backstop. Deleting a hand-typed entry deletes its catalogue row too, since nothing else can point at
it.

### `frontend/` — Vite 8, React 19, TypeScript, Tailwind 4

- `api/client.ts` — the only place `fetch` is called. Adds the CSRF header, throws `ApiError`.
- `api/hooks.ts` — TanStack Query hooks and the query keys. All server state goes through these;
  there are no hand-rolled loading flags.
- `auth/context.ts` holds the context and `useAuth`; `auth/AuthProvider.tsx` holds the component.
  They're split so react-refresh keeps working.
- `labels.ts` — **the app's whole vocabulary.** Status and type names live here only, so a thing is
  called the same on the button, the filter and the stats page. `COMPLETED` is "Finished" and
  `DROPPED` is "Gave up" to the user; `stats/StatsService.java` returns matching labels.
- `components/`, `pages/` — presentation. `pages/LibraryPage.tsx` keeps all filter state in the URL
  query string (debounced for the search box) so views are linkable and survive a reload.
- `components/TitleRow.tsx` — a provider result in a list, shared by `AddPage` and `ForYouPage` so a
  title looks and behaves the same wherever you meet it before it joins the library.

Tailwind 4 is configured entirely in `src/index.css` — no `tailwind.config.js`, no PostCSS config.
It's on 4 rather than 3 (which `weatherhub` uses) because Tailwind 3's `chokidar`/`braces` chain
carries five high-severity advisories.

## Design

Dark by deliberate choice, built around the artwork. The rules, which are worth keeping:

- **Posters are not cards.** No frame, no shadow, no hover lift. The artwork is the object.
- **One accent, spent on the score.** `--color-lamp` (projector amber) is essentially only used for
  the ten-notch score meter, active filters and focus rings. The score is what the app is *for*, so
  it's the brightest thing on screen.
- **The ten-notch meter is the signature element** (`ScoreMeter`, `ScoreInput`). An unscored entry
  still draws ten dim notches: "no verdict yet" is information.
- Tokens are defined once in `src/index.css` `@theme`. Every text pairing clears WCAG AA against its
  own background — re-check with a contrast calculator if you change one.
- Plain words in sentence case. No all-caps eyebrow labels, no `·`-joined meta strings (a hairline
  `border-l` separates metadata instead), no `→` appended to links, no numbered markers.
- Keyboard and screen-reader support is part of the floor: `ScoreInput` is a real radio group with
  arrow-key support, bars carry `aria-label`s, charts have a `<details>` table view, and
  `prefers-reduced-motion` is respected.

## Testing

Backend (109 tests): `IntegrationTest` is the base for anything needing the schema — it uses the
**singleton container pattern** on purpose. JUnit's `@Testcontainers`/`@Container` pair stops the
container when the first test class finishes, leaving every class after it talking to a dead
database; a static initialiser that never stops it avoids that. `TmdbProviderTest` and
`IgdbProviderTest` use `MockRestServiceServer` against fixtures in `src/test/resources/fixtures/`
and need no database or network. `RecommendationApiTest` stubs `MetadataProviders` with
`@MockitoBean` on purpose: what the providers return is the provider tests' job, and what matters at
that level is the scoring, the exclusions and the caching; `CatalogueRefresherTest` does the same.
`AccountApiTest` signs in for real and passes the `TRACKIT_SESSION` cookie around, because what it
tests is mostly what happens to sessions. `TmdbProviderTest` binds `MockRestServiceServer` with
`ignoreExpectOrder(true)` since seeds arrive in parallel.

Frontend (43 tests): Vitest + React Testing Library, with MSW stubbing the API
(`src/test/server.ts`). `npm run build` type-checks test files too, so a type error in a test breaks
the build — which is how the `onUnhandledFrame` rename in MSW 3 got caught.

## Gotchas

- `docker compose up` builds the backend inside a Maven image; the first build downloads a Maven
  repository into a BuildKit cache mount. Later builds are fast.
- `TRACKIT_SECURE_COOKIE=true` over plain HTTP makes sign-in fail with no visible error — the
  browser drops the cookie. Only set it behind HTTPS.
- `citext` needs the extension, created in `V1__init.sql`. It's in the standard `postgres` image.
- `MediaItem.genres`/`platforms` are `jsonb` mapped with `@JdbcTypeCode(SqlTypes.JSON)`, not
  Postgres arrays.
- nginx drops inherited `add_header` directives from any block that sets one of its own, so every
  such `location` in `nginx.conf` includes `security-headers.conf` again. The CSP allows images from
  any `http(s)` address (hand-typed artwork) and nothing else off-origin; React's `style` props go
  through the CSSOM, which `style-src 'self'` doesn't block.
- `RestClient.Builder` mutates in place, so the providers each `clone()` the shared bean before
  setting a base URL.
- Recommendations are only as good as the scores behind them, and a library added entirely by hand
  can never produce any. That is a property of the design, not a bug to fix.
