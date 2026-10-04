import { Link } from 'react-router-dom';

/** Every year with something finished in it, newest first. */
export default function YearLinks({ years, current }: { years: number[]; current?: number }) {
  return (
    <nav aria-label="Year in review" className="flex flex-wrap items-baseline gap-x-5 gap-y-2 text-sm">
      <span className="text-paper-dim">Year in review</span>
      {years.map((year) => (
        <Link
          key={year}
          to={`/stats/${year}`}
          aria-current={year === current ? 'page' : undefined}
          className={`border-b-2 pb-0.5 tabular-nums ${
            year === current
              ? 'border-lamp text-paper'
              : 'border-transparent text-paper-dim hover:text-paper'
          }`}
        >
          {year}
        </Link>
      ))}
    </nav>
  );
}
