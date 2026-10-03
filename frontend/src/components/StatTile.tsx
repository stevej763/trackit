export default function StatTile({
  value,
  label,
}: {
  value: string | number;
  label: string;
}) {
  return (
    <div>
      <div className="font-display text-4xl leading-none tabular-nums">{value}</div>
      <div className="mt-1.5 text-sm text-paper-dim">{label}</div>
    </div>
  );
}
