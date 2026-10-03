import type { ReactNode } from 'react';

/**
 * The shell for signing in and signing up. The brand mark is the ten-notch
 * score rule - the one thing this app is actually for - rather than a logo.
 */
export default function AuthCard({
  heading,
  intro,
  children,
  footer,
}: {
  heading: string;
  intro: string;
  children: ReactNode;
  footer: ReactNode;
}) {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-ink px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="font-display text-3xl tracking-tight">
          track<span className="text-lamp">it</span>
        </div>

        <div className="mt-4 mb-8 flex gap-0.5" aria-hidden="true">
          {Array.from({ length: 10 }, (_, index) => (
            <span
              key={index}
              className={`h-[3px] flex-1 rounded-[1px] ${index < 7 ? 'bg-lamp' : 'bg-edge'}`}
            />
          ))}
        </div>

        <h1 className="text-2xl">{heading}</h1>
        <p className="mt-2 mb-6 text-paper-dim">{intro}</p>

        {children}

        <div className="mt-6 text-sm text-paper-dim">{footer}</div>
      </div>
    </div>
  );
}
