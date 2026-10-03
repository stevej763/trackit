import type { ReactNode } from 'react';

/** An empty screen is an invitation to act, so it always ends in one. */
export default function EmptyState({
  heading,
  children,
  action,
}: {
  heading: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="border border-edge border-dashed rounded-lg px-6 py-14 text-center">
      <h2 className="text-xl">{heading}</h2>
      <p className="mx-auto mt-2 max-w-prose text-paper-dim">{children}</p>
      {action ? <div className="mt-6">{action}</div> : null}
    </div>
  );
}
