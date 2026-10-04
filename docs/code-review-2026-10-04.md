# TrackIt code review: bugs and improvements

Reviewed 2026-10-04, against `main` at commit `21a9d69`.

## Summary

TrackIt is in good shape: 10 real bugs, mostly at the edges, and none that lose data. The hard parts
(CSRF rotation, keeping each user's data private, null-last sorting, the no-API-key mode) are handled
carefully.

Scope: every file in the repo (backend, frontend, migrations, Docker, nginx, compose). Frontend tests
pass (19/19) and lint is clean. The backend suite was not run. The password bug (B4) was reproduced
against the `spring-security-crypto-6.5.11` jar in the built image; the rest come from reading the code.

## Bugs

Ranked most serious first. B1–B3 all hit the For You page and are the ones users will notice; B4 and
B5 are the 500s.

- [x] **B1. One failing TMDB seed breaks film/TV recommendations for good.**
  `TmdbProvider.java:100-123` makes one call per seed, and any failure (including a 404 for a title
  TMDB has removed) throws straight out of the loop. The comment says "a failure on one seed doesn't
  lose the others", but nothing catches it. Every refresh then makes up to 10 TMDB calls and shows an
  error or the stale cache, until that title's score drops below 7. No test covers it.
  *Fix:* catch `ProviderException` per seed and skip it; fail only if every seed fails. Add a test.

- [x] **B2. The For You "Refresh" button doesn't refresh.**
  `ForYouPage.tsx:163` calls `refetch()`, which requests `/recommendations?type=…` without
  `refresh=true`, so the server returns its cache. The `refresh` parameter in `client.ts:123` is never used.
  *Fix:* have the button call `api.recommendations(type, true)` and write the result into the query cache.

- [x] **B3. Titles you've added keep reappearing on For You.**
  Adding a title without a score doesn't change the cache fingerprint, so the cached list still
  contains it (`RecommendationService.java:87-93`). The "Added" marker is page state only, so after
  navigating away the title shows "Add" again and clicking it returns a 409. The marker is also keyed
  on `externalId` alone (`ForYouPage.tsx:28,190`), and TMDB film and TV ids overlap.
  *Fix:* filter cached items against `findAllTrackedExternalIds` on every read; key the marker on type + id.

- [x] **B4. Sign-up returns a 500 for passwords over 72 bytes.**
  `BCryptPasswordEncoder.encode` throws `IllegalArgumentException: password cannot be more than 72 bytes`
  (verified against the shipped jar), but `AuthDtos.java:22` allows 200 characters. Accented
  characters and emoji take several bytes each, so they hit the limit sooner.
  *Fix:* validate the length in UTF-8 bytes (max 72) with a clear message.

- [x] **B5. Adding the same title concurrently returns a 500.**
  `EntryService.java:162-169` catches `DataIntegrityViolationException` inside the `@Transactional`
  `create()` and then runs a SELECT. Postgres has already aborted the transaction, so the SELECT fails
  too. A double-clicked Add also gets past the check at `:83`, hits `entry_user_media_uk` and returns
  a 500 rather than a 409; `GlobalExceptionHandler` has no handler for it.
  *Fix:* `INSERT … ON CONFLICT DO NOTHING` then select (or a `REQUIRES_NEW` transaction); map
  constraint violations to 409.

- [x] **B6. "Saved." never appears on the item page.**
  `VerdictForm` is keyed on `updatedAt` (`ItemPage.tsx:95`). A successful save updates the cached
  entry, which remounts the form with fresh mutation state, so `isSuccess` at `:229` is always false.
  *Fix:* hold the success flag above the keyed form.

- [x] **B7. Clicking "Library" doesn't clear an active library search.**
  In `LibraryPage.tsx:59-79`, `queryText` is local state that survives navigation. When the URL loses
  `q`, the effect sees `''` differ from the debounced value and writes `q` back.
  *Fix:* sync `queryText` from the URL when `values.q` changes from outside.

- [x] **B8. An expired session isn't handled.**
  The `me` query has `staleTime: Infinity` (`AuthProvider.tsx:13-27`) and nothing reacts to a 401 from
  other requests. After the 30-day session ends, or after signing out in another tab, pages show
  "Please sign in" as an error instead of redirecting to sign-in.
  *Fix:* a global `QueryCache`/`MutationCache` `onError` that clears the user on a 401.

- [x] **B9. API secrets can end up in the logs.**
  The TMDB `api_key` and Twitch `client_secret` are sent in query strings (`TmdbProvider.java:135`,
  `IgdbTokenStore.java:53-58`). On a network error, the `ResourceAccessException` message includes the
  full URL, and `GlobalExceptionHandler.java:57` logs the cause.
  *Fix:* TMDB's v4 read-access token as a Bearer header; Twitch credentials as a form body.

- [x] **B10. TMDB ids aren't validated.**
  IGDB ids are checked against `\d{1,19}`; TMDB ids aren't (`TmdbProvider.java:67,101`). A crafted
  `POST /entries` with `externalId: "1/../../tv/1399"` or `"550/credits"` writes a mismatched or junk
  row into the shared catalogue.
  *Fix:* check against `\d{1,10}` before building the path.

### Minor

- [x] Changing the status of, or deleting, the last item on the last library page leaves you on an
  empty page that says "Your library is empty" and shows "Page 3 of 2" (`LibraryPage.tsx:160-203`).
- [x] Deleting a manual entry leaves its catalogue row behind (`EntryService.java:131`).
- [x] "Today" for finish dates is today in server UTC (`EntryService.java:90,113,125`), so finishing
  something late in the evening can record the wrong day. Consider letting the client send its local date.
- [x] The item page says "That title isn't in your library" for any error, including a 500 or a network failure.
- [x] `RecommendationService.java:193` logs the cache row id where the message says "user {}".
- [x] CLAUDE.md gives the frontend test count as 11 in one place and 19 in another; 19 is correct.

## Improvements

- [ ] **Move provider calls out of database transactions.** `RecommendationService.forUser` is
  `@Transactional` across up to 10 sequential TMDB calls (10 s timeout each), so it can hold a database
  connection for about 100 s; the default pool has 10. `EntryService.create` does the same on a smaller
  scale. Call the providers first, then open a short transaction. On Java 21, run the TMDB calls in
  parallel with virtual threads and an overall deadline.
- [ ] **Login protection.** Registration is open and login has no rate limiting or lockout. Add a
  per-IP/per-username throttle and a `TRACKIT_ALLOW_SIGNUP=false` switch for private instances.
- [ ] **Account management.** No way to change your password or delete your account.
- [ ] **Edit manual entries.** A typo in a hand-typed title means deleting it and losing the review.
  Also restrict the manual artwork URL to `http(s)`.
- [ ] **Refresh metadata.** `metadata_fetched_at` is stored but never used, so TV season counts and
  upcoming release years go stale. Add a "refresh details" action or a periodic job.
- [ ] **Backups.** Postgres sits on a named volume with no backup story. Add a `pg_dump` cron sidecar,
  an export endpoint, or both.
- [ ] **nginx security headers.** Add a CSP, `X-Content-Type-Options` and `Referrer-Policy`.
- [ ] **Test gaps.** One seed failing among several; a double-submitted add; a password over 72 bytes;
  the For You refresh button.

## Feature ideas

Roughly in order of value:

1. **Import/export.** Letterboxd/IMDb CSV, Trakt export, Steam library. Getting history in is the
   biggest barrier to using a tracker, and export gives you ownership of your data.
2. **A log of each watch or play.** `finishedOn` holds one date, so a rewatch overwrites the first.
   A list of dated events per entry fixes that and makes the stats more accurate.
3. **Progress tracking.** Season/episode for shows in progress, hours played for games.
4. **Where to watch.** TMDB's `/watch/providers` gives streaming services by region for the item page.
5. **Upcoming releases.** Highlight "Want to start" titles that come out in the next few weeks.
6. **Richer stats.** A year in review, total hours watched (runtimes are stored), a genre breakdown
   (genres are stored).
7. **Custom lists and tags.** For example "Christmas films" or "co-op games".
8. **"Pick something for me".** A random pick from "Want to start", filterable by type or runtime.
9. **An optional read-only share link** to your library or a single review.
10. **PWA support**, so it can be installed on a phone's home screen.

## Suggested order of work

1. B1–B3 (For You is visibly broken in normal use).
2. B4–B5 (user-facing 500s), then B9–B10 (security hygiene).
3. B6–B8 and the minor list.
4. Move provider calls out of transactions, then login throttling.
5. Features, starting with import/export and the watch log.
