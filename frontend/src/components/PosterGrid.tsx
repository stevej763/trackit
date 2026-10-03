import type { Entry, EntryStatus } from '../api/types';
import PosterCard from './PosterCard';

interface Props {
  entries: Entry[];
  onStatusChange: (id: number, status: EntryStatus) => void;
}

export default function PosterGrid({ entries, onStatusChange }: Props) {
  return (
    <div className="grid grid-cols-2 gap-x-4 gap-y-7 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
      {entries.map((entry, index) => (
        <PosterCard
          key={entry.id}
          entry={entry}
          priority={index < 6}
          onStatusChange={(status) => onStatusChange(entry.id, status)}
        />
      ))}
    </div>
  );
}
