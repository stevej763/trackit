import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/render';
import { server } from '../test/server';
import SignInPage from './SignInPage';

describe('SignInPage', () => {
  // Signed out, so the page renders rather than redirecting.
  function signedOut() {
    server.use(
      http.get('/api/auth/me', () =>
        HttpResponse.json({ error: 'unauthenticated', message: 'Please sign in' }, { status: 401 }),
      ),
    );
  }

  it('offers sign-up when the server takes new accounts', async () => {
    signedOut();
    renderWithProviders(<SignInPage />, { route: '/signin' });

    expect(await screen.findByRole('link', { name: 'Create one' })).toHaveAttribute('href', '/signup');
  });

  it('says sign-ups are closed instead of offering a dead end', async () => {
    signedOut();
    server.use(http.get('/api/auth/options', () => HttpResponse.json({ signupAllowed: false })));
    renderWithProviders(<SignInPage />, { route: '/signin' });

    expect(await screen.findByText(/isn’t taking new accounts/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Create one' })).not.toBeInTheDocument();
  });

  it('shows the server’s message when sign-ins are being throttled', async () => {
    signedOut();
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json(
          { error: 'too_many_requests', message: 'Too many sign-in attempts. Try again in 15 minutes.' },
          { status: 429 },
        ),
      ),
    );
    renderWithProviders(<SignInPage />, { route: '/signin' });

    const userEvent = (await import('@testing-library/user-event')).default;
    await userEvent.type(await screen.findByLabelText('Username'), 'steve');
    await userEvent.type(screen.getByLabelText('Password'), 'password1234');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Try again in 15 minutes');
  });
});
