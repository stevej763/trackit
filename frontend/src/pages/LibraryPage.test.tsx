import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Route, Routes, useLocation } from 'react-router-dom';
import { renderWithProviders } from '../test/render';
import { entriesRequests } from '../test/server';
import LibraryPage from './LibraryPage';

/** Surfaces the router's query string so we can assert the URL is the source of truth. */
function LocationProbe() {
  return <div data-testid="search">{useLocation().search}</div>;
}

function renderLibrary(route = '/') {
  return renderWithProviders(
    <>
      <LocationProbe />
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
});
