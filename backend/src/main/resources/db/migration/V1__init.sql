-- Case-insensitive usernames without having to lower() every comparison.
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE app_user (
    id            bigserial PRIMARY KEY,
    username      citext      NOT NULL UNIQUE,
    password_hash text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);

-- The shared catalogue. One row per real-world title, regardless of how many
-- users track it, so metadata is fetched from TMDB/IGDB once.
CREATE TABLE media_item (
    id                  bigserial PRIMARY KEY,
    media_type          text NOT NULL CHECK (media_type IN ('MOVIE', 'TV', 'GAME')),
    source              text NOT NULL CHECK (source IN ('TMDB', 'IGDB', 'MANUAL')),
    external_id         text,
    title               text NOT NULL,
    release_year        int,
    overview            text,
    poster_url          text,
    backdrop_url        text,
    runtime_minutes     int,            -- movies
    season_count        int,            -- tv
    episode_count       int,            -- tv
    platforms           jsonb,          -- games, array of strings
    genres              jsonb,          -- array of strings
    metadata_fetched_at timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now()
);

-- Manual items have no external id, so the uniqueness rule only applies to
-- provider-sourced rows.
CREATE UNIQUE INDEX media_item_source_external_uk
    ON media_item (source, media_type, external_id)
    WHERE external_id IS NOT NULL;

-- One row per (user, title): the user's own status, score and review.
CREATE TABLE entry (
    id            bigserial PRIMARY KEY,
    user_id       bigint   NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    media_item_id bigint   NOT NULL REFERENCES media_item (id) ON DELETE CASCADE,
    status        text     NOT NULL CHECK (status IN ('WANT', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'DROPPED')),
    rating        smallint CHECK (rating BETWEEN 1 AND 10),
    review        text,
    started_on    date,
    finished_on   date,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT entry_user_media_uk UNIQUE (user_id, media_item_id)
);

CREATE INDEX entry_user_status_idx      ON entry (user_id, status);
CREATE INDEX entry_user_created_at_idx  ON entry (user_id, created_at DESC);
CREATE INDEX entry_user_finished_on_idx ON entry (user_id, finished_on DESC);
