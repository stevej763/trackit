import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { Link, Route, Routes, useLocation } from 'react-router-dom';
import { renderWithProviders } from '../test/render';
import { entriesRequests, makeEntry, server } from '../test/server';
import LibraryPage from './LibraryPage';

/** Surfaces the router's query string so we can assert the URL is the source of truth. */
function LocationProbe() {
  return <div data-testid="search">{useLocation().search}</div>;
}

function renderLibrary(route = '/') {
  return renderWithProviders(
    <>
      <LocationProbe />
      {/* Stands in for the app shell's own Library link. */}
      <Link to="/">Library</Link>
      <Routes>
        <Route path="/" element={<LibraryPage />} />
      </Routes>
    </>,
    { route },
  );
}

describe('LibraryPage filters', () => {
  it('puts the chosen type in the URL and in the request', async () => {
    renderLibrary();
    await screen.findByText('Severance');

    await userEvent.click(screen.getByRole('button', { name: 'Films' }));

    await waitFor(() => expect(screen.getByTestId('search').textContent).toContain('type=movie'));
    await waitFor(() => expect(entriesRequests.at(-1)).toContain('type=movie'));
  });

  it('reads its initial state back out of the URL', async () => {
    renderLibrary('/?type=game&status=completed&sort=rating&dir=asc');
    await screen.findByText('Severance');

    expect(screen.getByRole('button', { name: 'Games' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Finished' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByLabelText('Sort by')).toHaveValue('rating');

    const request = entriesRequests.at(-1) ?? '';
    expect(request).toContain('type=game');
    expect(request).toContain('status=completed');
    expect(request).toContain('sort=rating');
    expect(request).toContain('direction=asc');
  });

  it('applies each sort its natural direction', async () => {
    renderLibrary();
    await screen.findByText('Severance');

    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'title');

    // Titles should start at A, not Z.
    await waitFor(() => expect(screen.getByTestId('search').textContent).toContain('dir=asc'));
  });

  it('describes how many titles matched', async () => {
    renderLibrary();
    expect(await screen.findByText('1 title')).toBeInTheDocument();
  });

  it('clears the search when the Library link is followed', async () => {
    renderLibrary('/?q=dune');
    const box = await screen.findByRole('searchbox', { name: 'Search your library' });
    expect(box).toHaveValue('dune');

    await userEvent.click(screen.getByRole('link', { name: 'Library' }));

    expect(box).toHaveValue('');
    // Past the debounce, the old term must not have been written back.
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(screen.getByTestId('search').textContent).toBe('');
  });

  it('still writes a typed search into the URL', async () => {
    renderLibrary();
    const box = await screen.findByRole('searchbox', { name: 'Search your library' });

    await userEvent.type(box, 'sev');

    await waitFor(() => expect(screen.getByTestId('search').textContent).toContain('q=sev'));
    expect(box).toHaveValue('sev');
  });

  it('steps back to the last page when the one it was on empties', async () => {
    server.use(
      http.get('/api/entries', ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'));
        // Two pages of results, so page 3 (index 2) is past the end.
        return HttpResponse.json({
          items: page === 1 ? [makeEntry()] : [],
          page,
          size: 48,
          totalItems: 49,
          totalPages: 2,
        });
      }),
    );
    renderLibrary('/?page=2');

    await waitFor(() => expect(screen.getByTestId('search').textContent).toBe('?page=1'));
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument();
    expect(screen.queryByText('Your library is empty')).not.toBeInTheDocument();
  });
});
