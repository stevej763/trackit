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

Registration is open by default: anyone who can reach the app can create an
account. Once your own accounts exist, set `TRACKIT_ALLOW_SIGNUP=false` to close
it. Repeated failed sign-ins are throttled either way.

### API keys

TrackIt works with no keys at all — you just add titles by hand instead of
searching for them. To get artwork and details automatically, fill these in
`.env`:

| Variable | Where to get it |
| --- | --- |
| `TMDB_API_KEY` | Sign up at [themoviedb.org](https://www.themoviedb.org/signup), then **Settings → API → Create** and request a Developer key. Copy the long **API Read Access Token** (starts with `eyJ`), which is sent as a header and never appears in a URL or log. The 32-character **API Key (v3 auth)** also works. |
| `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` | IGDB authenticates through Twitch. Create a Twitch account, turn on two-factor auth, then register an app at [dev.twitch.tv](https://dev.twitch.tv/console/apps/create) (redirect URL `http://localhost`, category "Application Integration"). The app's Client ID and a generated secret go here. |

Both are free. TMDB covers films and TV; IGDB covers games.

### Other settings

| Variable | Default | Notes |
| --- | --- | --- |
| `TRACKIT_PORT` | `8300` | Host port the app is served on. |
| `DB_HOST` / `DB_PORT` | `db` / `5432` | Where Postgres is. `db` is the local container; see [Production](#production-with-an-external-database). |
| `DB_SSLMODE` | `prefer` | `require` or stricter for a database across a network. |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | `trackit` | Change the password before putting this anywhere real. |
| `TRACKIT_SECURE_COOKIE` | `false` | Set to `true` only behind HTTPS. A `Secure` cookie is dropped over plain HTTP, which makes signing in fail silently. |
| `TRACKIT_ALLOW_SIGNUP` | `true` | `false` stops new accounts. Existing ones still sign in. |
| `TRACKIT_METADATA_REFRESH_CRON` | `0 30 4 * * *` | When to re-fetch details likely to have changed (season counts, recent release years), in UTC. `-` turns it off. |
| `BACKUP_INTERVAL_HOURS` / `BACKUP_KEEP_DAYS` | `24` / `14` | How often the backup service dumps the database, and how long dumps are kept. |
| `BACKUP_PG_VERSION` | `16` | The backup's `pg_dump` version. At least your Postgres server's major version. |
| `TRACKIT_LOG_LEVEL` | `INFO` | `DEBUG` for more detail on provider calls. |

### Production with an external database

`compose.yaml` runs the app against whatever Postgres `DB_HOST` names.
`compose.override.yaml`, which plain `docker compose` merges in automatically,
adds the local `db` container. To use your own Postgres instead:

1. Create the database and an owner for it on that server:
   ```sql
   CREATE ROLE trackit LOGIN PASSWORD '...';
   CREATE DATABASE trackit OWNER trackit;
   ```
   The api creates the tables (and the `citext` extension) on its first start.
2. In `.env`, uncomment `COMPOSE_FILE=compose.yaml`, so the override is skipped
   and no `db` container is created, then set `DB_HOST`, `DB_PORT`,
   `POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB`, and usually
   `DB_SSLMODE=require`. For a Postgres on the Docker host itself, use
   `DB_HOST=host.docker.internal`.
3. Set `BACKUP_PG_VERSION` to the server's major version (or newer).
4. `docker compose up -d --build`.

Behind an HTTPS reverse proxy, also set `TRACKIT_SECURE_COOKIE=true`.

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
- **Suggestions, with reasons.** The **For you** page builds a list from the
  titles you scored 7 or higher, and says which of them each suggestion came
  from. Score a couple of things you love and it fills up; things you already
  track never show up. Needs the API keys below, and titles added by search —
  hand-typed entries have nothing to match on.
- **Stats.** Counts by type and status, how your scores are distributed, and what
  you finished each month over the last year.
- **Details that keep up.** Shows and recent titles have their details re-fetched
  nightly, and any provider title can be refreshed from its page. Hand-typed
  titles can be edited without losing the score and review.
- **Your account, your data.** Change your password (which signs you out
  everywhere else), download your whole library as JSON, or delete the account
  and everything in it.

Accounts are fully separate: two people on the same instance share the metadata
cache and nothing else.

## Running the tests

```sh
docker compose run --rm api-test          # backend: 109 tests, no local Java needed
cd frontend && npm install && npm test    # frontend: Vitest + React Testing Library
cd frontend && npm run lint && npm run build
```

## Backups

Everything lives in Postgres: the `db_data` volume locally, or your external
server. The `backup` service writes a `pg_dump` of it to `./backups` when it
starts and every 24 hours after that, keeping two weeks of dumps. Copy that
folder somewhere off the machine too.

To restore one (against either database, through the backup service's own
client):

```sh
docker compose stop api
docker compose run --rm -T --no-deps --entrypoint sh backup \
  -c 'pg_restore --clean --if-exists --no-owner -d "$PGDATABASE"' \
  < backups/trackit-20261004T043000Z.dump
docker compose start api
```

Each user can also download their own library from the Account page.

## Development

See [CLAUDE.md](CLAUDE.md) for the architecture, the data model, and how to work
on each half without Docker.
