-- A finish date was a bare `date`, and "today" was today on the server's UTC
-- clock, so finishing something late in the evening west of Greenwich (or early
-- morning east of it) recorded the wrong day. Store the moment instead; the day
-- it falls on is worked out in the user's own time zone when it's shown, and
-- the stats page buckets by month in the zone the browser sends.
--
-- Existing dates become midday UTC on that date, which lands on the same
-- calendar day everywhere from UTC-12 to UTC+11, rather than midnight, which
-- would show as the day before anywhere west of Greenwich.
ALTER TABLE entry RENAME COLUMN finished_on TO finished_at;

ALTER TABLE entry
    ALTER COLUMN finished_at TYPE timestamptz
    USING (finished_at + time '12:00') AT TIME ZONE 'UTC';

ALTER INDEX entry_user_finished_on_idx RENAME TO entry_user_finished_at_idx;
