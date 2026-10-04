import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/render';
import { makeRecommendation, makeRecommendations, server } from '../test/server';
import ForYouPage from './ForYouPage';

function respondWith(body: object, status = 200) {
  server.use(http.get('/api/recommendations', () => HttpResponse.json(body, { status })));
}

describe('ForYouPage', () => {
  it('says why each suggestion is there', async () => {
    renderWithProviders(<ForYouPage />);

    expect(await screen.findByText('Dune')).toBeInTheDocument();
    // A recommendation you can't account for is just noise, so the reason is
    // part of the row, not a tooltip.
    expect(screen.getByText(/Because you rated/)).toBeInTheDocument();
    expect(screen.getByText('Dune: Part Two')).toBeInTheDocument();
  });

  it('counts the other titles that agreed', async () => {
    respondWith(
      makeRecommendations({
        items: [makeRecommendation({ seedMatches: 3 })],
        seedCount: 3,
      }),
    );
    renderWithProviders(<ForYouPage />);

    expect(await screen.findByText(/and 2 more you liked/)).toBeInTheDocument();
  });

  it('asks for scores before it can suggest anything', async () => {
    respondWith(makeRecommendations({ items: [], seedCount: 0, ratedCount: 0 }));
    renderWithProviders(<ForYouPage />);

    expect(await screen.findByRole('heading', { name: 'Score a few things first' })).toBeInTheDocument();
  });

  it('explains when the only scored titles were typed in by hand', async () => {
    // Scored things exist, but none of them came from a provider, so there is
    // no id to match on. That is a different problem from having no scores.
    respondWith(makeRecommendations({ items: [], seedCount: 0, ratedCount: 4 }));
    renderWithProviders(<ForYouPage />);

    expect(
      await screen.findByRole('heading', { name: /Nothing to match your films against/ }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Hand-typed entries/)).toBeInTheDocument();
  });

  it('says so when everything suggested is already tracked', async () => {
    respondWith(makeRecommendations({ items: [], seedCount: 5, ratedCount: 5 }));
    renderWithProviders(<ForYouPage />);

    expect(await screen.findByRole('heading', { name: 'Nothing new to suggest' })).toBeInTheDocument();
  });

  it('shows the provider message and offers manual entry when a provider is down', async () => {
    respondWith(
      { error: 'provider_unavailable', message: 'Couldn’t reach TMDB. Check the server’s network access.' },
      503,
    );
    renderWithProviders(<ForYouPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Couldn’t reach TMDB');
    expect(screen.getByRole('link', { name: /Add something by hand/ })).toBeInTheDocument();
  });

  it('switches media type without losing the page', async () => {
    renderWithProviders(<ForYouPage />);
    await screen.findByText('Dune');

    await userEvent.click(screen.getByRole('button', { name: 'Games' }));

    expect(screen.getByRole('button', { name: 'Games' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Films' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('asks the server to rebuild when Refresh is pressed, not for its cache', async () => {
    const requests: string[] = [];
    server.use(
      http.get('/api/recommendations', ({ request }) => {
        const url = new URL(request.url);
        requests.push(url.search);
        return HttpResponse.json(
          url.searchParams.get('refresh') === 'true'
            ? makeRecommendations({ items: [makeRecommendation({ title: 'Arrival', externalId: '329865' })] })
            : makeRecommendations({ fromCache: true }),
        );
      }),
    );
    renderWithProviders(<ForYouPage />);
    await screen.findByText('Dune');

    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }));

    expect(await screen.findByText('Arrival')).toBeInTheDocument();
    await waitFor(() => expect(requests.at(-1)).toContain('refresh=true'));
  });

  it('reports a suggestion as added once it is in the library', async () => {
    server.use(
      http.post('/api/entries', () => HttpResponse.json({ id: 9 }, { status: 201 })),
    );
    renderWithProviders(<ForYouPage />);
    await screen.findByText('Dune');

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByText('Added')).toBeInTheDocument();
  });

  it('marks only the added title, not one of another type sharing its id', async () => {
    // TMDB numbers films and shows separately, so the same id can be both.
    respondWith(
      makeRecommendations({
        items: [
          makeRecommendation({ title: 'A film', mediaType: 'MOVIE', externalId: '100' }),
          makeRecommendation({ title: 'A show', mediaType: 'TV', externalId: '100' }),
        ],
      }),
    );
    server.use(http.post('/api/entries', () => HttpResponse.json({ id: 9 }, { status: 201 })));
    renderWithProviders(<ForYouPage />);
    await screen.findByText('A film');

    await userEvent.click(screen.getAllByRole('button', { name: 'Add' })[0]);

    expect(await screen.findByText('Added')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Add' })).toHaveLength(1);
  });
});
