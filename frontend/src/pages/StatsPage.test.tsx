import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/render';
import { makeStats, server } from '../test/server';
import StatsPage from './StatsPage';

describe('StatsPage', () => {
  it('counts the hours of film you have finished', async () => {
    renderWithProviders(<StatsPage />);

    const label = await screen.findByText('hours of film');
    // 293 minutes, rounded to the hour.
    expect(label.previousElementSibling).toHaveTextContent(/^5$/);
  });

  it('says when the film total is short', async () => {
    server.use(
      http.get('/api/stats', () =>
        HttpResponse.json(makeStats({ filmTime: { minutes: 45, filmsWithoutRuntime: 2 } })),
      ),
    );
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('minutes of film')).toBeInTheDocument();
    expect(screen.getByText('Not counting 2 films with no running time.')).toBeInTheDocument();
  });

  it('lists genres with their count and average score', async () => {
    renderWithProviders(<StatsPage />);

    const drama = await screen.findByRole('row', { name: /Drama/ });
    expect(within(drama).getByText('3')).toBeInTheDocument();
    expect(within(drama).getByText('7.5')).toBeInTheDocument();
    // Nothing scored in that genre yet.
    expect(within(screen.getByRole('row', { name: /Crime/ })).getByText('--')).toBeInTheDocument();
  });

  it('links to each year in review', async () => {
    renderWithProviders(<StatsPage />);

    const years = await screen.findByRole('navigation', { name: 'Year in review' });
    expect(within(years).getByRole('link', { name: '2025' })).toHaveAttribute('href', '/stats/2025');
  });

  it('leaves out the genres panel when nothing has a genre', async () => {
    server.use(http.get('/api/stats', () => HttpResponse.json(makeStats({ genres: [] }))));
    renderWithProviders(<StatsPage />);

    await screen.findByText('hours of film');
    expect(screen.queryByRole('heading', { name: 'What you are into' })).not.toBeInTheDocument();
  });
});
