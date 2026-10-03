package dev.stevejones.trackit.entry;

/**
 * Where a title sits in the user's queue. Deliberately the same five values for
 * all three media types &mdash; "watching" and "playing" are the same idea.
 */
public enum EntryStatus {
    WANT,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    DROPPED
}
