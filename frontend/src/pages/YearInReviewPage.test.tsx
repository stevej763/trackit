import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/render';
import { makeYear, server } from '../test/server';
import YearInReviewPage from './YearInReviewPage';

function renderYear(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/stats" element={<p>All stats</p>} />
      <Route path="/stats/:year" element={<YearInReviewPage />} />
    </Routes>,
    { route },
  );
}

describe('YearInReviewPage', () => {
  it('shows the year at a glance, best first', async () => {
    renderYear('/stats/2025');

    expect(await screen.findByRole('heading', { name: 'Your 2025' })).toBeInTheDocument();
    expect(screen.getByText('8.7')).toBeInTheDocument();
    expect(screen.getByText('Not counting 1 film with no running time.')).toBeInTheDocument();

    const best = screen.getByRole('heading', { name: 'Best of 2025' }).closest('section')!;
    const titles = within(best).getAllByRole('link', { name: /Heat|Thief/ });
    expect(titles[0]).toHaveAttribute('href', '/item/7');
    expect(within(best).getByRole('img', { name: 'Scored 9 out of 10' })).toBeInTheDocument();
  });

  it('marks the year being shown', async () => {
    renderYear('/stats/2025');

    const years = await screen.findByRole('navigation', { name: 'Year in review' });
    expect(within(years).getByRole('link', { name: '2025' })).toHaveAttribute('aria-current', 'page');
    expect(within(years).getByRole('link', { name: '2026' })).not.toHaveAttribute('aria-current');
  });

  it('says when nothing was finished that year', async () => {
    server.use(
      http.get('/api/stats/years/:year', () =>
        HttpResponse.json(makeYear({ year: 2024, finished: 0, best: [], genres: [] })),
      ),
    );
    renderYear('/stats/2024');

    expect(await screen.findByRole('heading', { name: 'Nothing finished in 2024' })).toBeInTheDocument();
  });

  it('sends anything that is not a year back to the stats page', async () => {
    renderYear('/stats/last');

    expect(await screen.findByText('All stats')).toBeInTheDocument();
  });
});
