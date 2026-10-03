-- Recommendations cost one provider round-trip per seed title (TMDB) and are
-- recomputed rarely, so the result is cached per user and media type.
--
-- seed_fingerprint is a hash of the seed ids and the scores given to them, so
-- scoring something new invalidates the cache immediately rather than leaving
-- the user waiting out the time-based expiry.
CREATE TABLE recommendation_cache (
    id               bigserial PRIMARY KEY,
    user_id          bigint      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    media_type       text        NOT NULL CHECK (media_type IN ('MOVIE', 'TV', 'GAME')),
    seed_fingerprint text        NOT NULL,
    computed_at      timestamptz NOT NULL DEFAULT now(),
    -- The serialised response. Opaque: nothing queries inside it, so it is held
    -- as text and (de)serialised in the service rather than mapped as jsonb.
    payload          text        NOT NULL,
    CONSTRAINT recommendation_cache_user_type_uk UNIQUE (user_id, media_type)
);
