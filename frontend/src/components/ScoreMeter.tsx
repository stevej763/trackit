const NOTCHES = Array.from({ length: 10 }, (_, index) => index + 1);

/**
 * The ten-notch score, read-only. This is the one place the accent colour is
 * spent, so a glance down a grid of posters reads as a row of verdicts.
 *
 * An unscored entry still draws ten dim notches rather than nothing: "no
 * verdict yet" is information, and it keeps every caption the same height.
 */
export default function ScoreMeter({ rating }: { rating: number | null }) {
  return (
    <div
      className="flex gap-0.5"
      role="img"
      aria-label={rating ? `Scored ${rating} out of 10` : 'Not scored yet'}
    >
      {NOTCHES.map((notch) => (
        <span
          key={notch}
          className={`h-[3px] flex-1 rounded-[1px] ${
            rating && notch <= rating ? 'bg-lamp' : 'bg-edge'
          }`}
        />
      ))}
    </div>
  );
}
