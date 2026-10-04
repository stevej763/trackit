export default function StatTile({
  value,
  label,
  note,
}: {
  value: string | number;
  label: string;
  /** A caveat on the number, in small print under the label. */
  note?: string;
}) {
  return (
    <div>
      <div className="font-display text-4xl leading-none tabular-nums">{value}</div>
      <div className="mt-1.5 text-sm text-paper-dim">{label}</div>
      {note ? <div className="mt-1 text-xs text-paper-dim">{note}</div> : null}
    </div>
  );
}
