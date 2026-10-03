import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/render';
import { server } from '../test/server';
import SignInPage from '../pages/SignInPage';
import RequireAuth from './RequireAuth';

function renderProtected(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/signin" element={<SignInPage />} />
      <Route element={<RequireAuth />}>
        <Route path="/stats" element={<h1>Stats</h1>} />
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
});
