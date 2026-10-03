import type { ReactNode } from 'react';

interface Props {
  title: string;
  releaseYear: number | null;
  overview: string | null;
  posterUrl: string | null;
  /** An extra line under the overview, e.g. why this was suggested. */
  note?: ReactNode;
  /** The right-hand control: add it, or a link to the copy you already have. */
  action: ReactNode;
}

/**
 * A provider result in a list, shared by the Add and For you pages so a title
 * looks and behaves the same wherever you meet it before it joins the library.
 */
export default function TitleRow({ title, releaseYear, overview, posterUrl, note, action }: Props) {
  return (
    <div className="flex gap-4 py-4">
      <div className="h-24 w-16 shrink-0 overflow-hidden rounded-sm bg-surface">
        {posterUrl ? (
          <img src={posterUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
        ) : null}
      </div>

      <div className="min-w-0 flex-1">
        <h2 className="text-base font-normal">
          {title}
          {releaseYear ? <span className="ml-2 text-paper-dim">{releaseYear}</span> : null}
        </h2>
        {overview ? (
          <p className="mt-1 line-clamp-2 max-w-[68ch] text-sm text-paper-dim">{overview}</p>
        ) : null}
        {note ? <p className="mt-1.5 text-sm text-paper-dim">{note}</p> : null}
      </div>

      <div className="shrink-0 self-center">{action}</div>
    </div>
  );
}
