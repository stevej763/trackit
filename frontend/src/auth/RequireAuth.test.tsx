import { useQuery } from '@tanstack/react-query';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { api } from '../api/client';
import { renderWithProviders } from '../test/render';
import { server, steve } from '../test/server';
import SignInPage from '../pages/SignInPage';
import RequireAuth from './RequireAuth';

/** A page whose own query can come back 401. */
function Library() {
  const { isError } = useQuery({ queryKey: ['entries'], queryFn: () => api.stats() });
  return <h1>{isError ? 'Please sign in' : 'Library'}</h1>;
}

function renderProtected(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/signin" element={<SignInPage />} />
      <Route element={<RequireAuth />}>
        <Route path="/stats" element={<h1>Stats</h1>} />
        <Route path="/library" element={<Library />} />
      </Route>
    </Routes>,
    { route },
  );
}

describe('RequireAuth', () => {
  it('sends an unauthenticated visitor to sign in', async () => {
    server.use(
      http.get('/api/auth/me', () =>
        HttpResponse.json({ error: 'unauthenticated', message: 'Please sign in' }, { status: 401 }),
      ),
    );

    renderProtected('/stats');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Stats' })).not.toBeInTheDocument();
  });

  it('lets a signed-in visitor through', async () => {
    renderProtected('/stats');

    expect(await screen.findByRole('heading', { name: 'Stats' })).toBeInTheDocument();
  });

  it('sends the user to sign in when their session ends under an open page', async () => {
    // Signed in on boot, but the session has since expired (or another tab
    // signed out), so the page's own request comes back 401.
    server.use(
      http.get('/api/stats', () =>
        HttpResponse.json({ error: 'unauthenticated', message: 'Please sign in' }, { status: 401 }),
      ),
    );
    // Only the boot-time check finds a session.
    server.use(
      http.get('/api/auth/me', () => HttpResponse.json(steve), { once: true }),
      http.get('/api/auth/me', () =>
        HttpResponse.json({ error: 'unauthenticated', message: 'Please sign in' }, { status: 401 }),
      ),
    );

    renderProtected('/library');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });
});
