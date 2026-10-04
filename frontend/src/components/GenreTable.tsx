import type { Genre } from '../api/types';

/**
 * Genres with how many titles carry each and what they score on average.
 *
 * A real table, because there are two numbers per row: the bar is a visual
 * aid to the count beside it, so the table needs no separate fallback.
 */
export default function GenreTable({ genres }: { genres: Genre[] }) {
  const max = Math.max(...genres.map((genre) => genre.count), 1);

  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-paper-dim">
          <th scope="col" className="pb-2 text-left font-normal">
            Genre
          </th>
          <th scope="col" className="pb-2 text-right font-normal">
            Titles
          </th>
          <th scope="col" className="pb-2 pl-4 text-right font-normal">
            Average score
          </th>
        </tr>
      </thead>
      <tbody>
        {genres.map((genre) => (
          <tr key={genre.name}>
            <th scope="row" className="py-1.5 pr-3 text-left font-normal text-paper-dim">
              <span className="block max-w-[9rem] truncate">{genre.name}</span>
            </th>
            <td className="py-1.5">
              <div className="grid grid-cols-[1fr_2.5rem] items-center gap-3">
                <span className="h-2 rounded-r-sm bg-edge/40" aria-hidden="true">
                  <span
                    className="block h-2 rounded-r-sm bg-lamp"
                    style={{ width: `${(genre.count / max) * 100}%` }}
                  />
                </span>
                <span className="text-right tabular-nums">{genre.count}</span>
              </div>
            </td>
            <td className="py-1.5 pl-4 text-right tabular-nums">{genre.average ?? '--'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
