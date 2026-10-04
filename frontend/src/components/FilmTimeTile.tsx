import type { FilmTime } from '../api/types';
import { splitDuration } from '../labels';
import StatTile from './StatTile';

/** Time spent on finished films, with a caveat when some don't say how long they are. */
export default function FilmTimeTile({ filmTime }: { filmTime: FilmTime }) {
  const { value, unit } = splitDuration(filmTime.minutes);
  const missing = filmTime.filmsWithoutRuntime;

  return (
    <StatTile
      value={value}
      label={`${unit} of film`}
      note={
        missing > 0
          ? `Not counting ${missing === 1 ? '1 film' : `${missing} films`} with no running time.`
          : undefined
      }
    />
  );
}
