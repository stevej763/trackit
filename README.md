# TrackIt

A self-hosted web app for tracking the films, TV shows and games you're watching,
playing, or mean to get to. Search a title, add it with its artwork, and record a
score out of 10 and a written review.

Java/Spring Boot API, PostgreSQL, React frontend, one `docker compose up`.

![ports](https://img.shields.io/badge/port-8300-F0A826) ![licence](https://img.shields.io/badge/use-personal-96A0B5)

## Getting started

```sh
cp .env.example .env     # then edit it, see below
docker compose up --build
```

Open <http://localhost:8300>, create an account, and start adding things.

Registration is open: anyone who can reach the app can create an account. That's
fine on a home network; think twice before exposing it to the internet.

### API keys

TrackIt works with no keys at all — you just add titles by hand instead of
searching for them. To get artwork and details automatically, fill these in
`.env`:

| Variable | Where to get it |
| --- | --- |
| `TMDB_API_KEY` | Sign up at [themoviedb.org](https://www.themoviedb.org/signup), then **Settings → API → Create** and request a Developer key. Copy the 32-character **API Key (v3 auth)**, not the longer read access token. |
| `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` | IGDB authenticates through Twitch. Create a Twitch account, turn on two-factor auth, then register an app at [dev.twitch.tv](https://dev.twitch.tv/console/apps/create) (redirect URL `http://localhost`, category "Application Integration"). The app's Client ID and a generated secret go here. |

Both are free. TMDB covers films and TV; IGDB covers games.

### Other settings

| Variable | Default | Notes |
| --- | --- | --- |
| `TRACKIT_PORT` | `8300` | Host port the app is served on. |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | `trackit` | Change the password before putting this anywhere real. |
| `TRACKIT_SECURE_COOKIE` | `false` | Set to `true` only behind HTTPS. A `Secure` cookie is dropped over plain HTTP, which makes signing in fail silently. |
| `TRACKIT_LOG_LEVEL` | `INFO` | `DEBUG` for more detail on provider calls. |

## What it does

- **One library, three kinds of thing.** Films, TV and games sit together, each
  with the same five statuses: want to start, in progress, on hold, finished,
  gave up.
- **Search brings the details with it.** Pick a result and the title, year,
  artwork, runtime or season count, platforms and genres come along. Anything the
  providers don't have, you can type in by hand.
- **A score and a review.** One number out of 10 plus as much prose as you like.
- **Filter and sort.** By type, status, minimum score or title, sorted by date
  added, title, score, finish date or release year. The filters live in the URL,
  so any view can be bookmarked.
- **Stats.** Counts by type and status, how your scores are distributed, and what
  you finished each month over the last year.

Accounts are fully separate: two people on the same instance share the metadata
cache and nothing else.

## Running the tests

```sh
docker compose run --rm api-test          # backend: 42 tests, no local Java needed
cd frontend && npm install && npm test    # frontend: Vitest + React Testing Library
cd frontend && npm run lint && npm run build
```

## Backups

Everything lives in the `db_data` volume.

```sh
docker compose exec db pg_dump -U trackit trackit > trackit-$(date +%F).sql
```

## Development

See [CLAUDE.md](CLAUDE.md) for the architecture, the data model, and how to work
on each half without Docker.
