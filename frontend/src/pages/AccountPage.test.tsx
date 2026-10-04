import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/render';
import { server } from '../test/server';
import AccountPage from './AccountPage';

function renderAccount() {
  return renderWithProviders(
    <Routes>
      <Route path="/account" element={<AccountPage />} />
      <Route path="/signin" element={<p>Sign-in page</p>} />
    </Routes>,
    { route: '/account' },
  );
}

describe('AccountPage', () => {
  it('changes the password and clears the form', async () => {
    let sent: unknown = null;
    server.use(
      http.put('/api/account/password', async ({ request }) => {
        sent = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderAccount();

    await userEvent.type(await screen.findByLabelText(/^Current password/), 'password1234');
    await userEvent.type(screen.getByLabelText(/^New password/), 'a much better one');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByText('Password changed.')).toBeInTheDocument();
    expect(sent).toEqual({ currentPassword: 'password1234', newPassword: 'a much better one' });
    expect(screen.getByLabelText(/^Current password/)).toHaveValue('');
  });

  it('puts a wrong current password next to the field', async () => {
    server.use(
      http.put('/api/account/password', () =>
        HttpResponse.json(
          {
            error: 'validation_failed',
            message: 'Please check the highlighted fields',
            details: { currentPassword: 'That isn’t your current password' },
          },
          { status: 400 },
        ),
      ),
    );
    renderAccount();

    await userEvent.type(await screen.findByLabelText(/^Current password/), 'a guess');
    await userEvent.type(screen.getByLabelText(/^New password/), 'a much better one');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByText('That isn’t your current password')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Current password/)).toHaveAccessibleDescription(
      'That isn’t your current password',
    );
  });

  it('offers the library as a download', async () => {
    renderAccount();

    const link = await screen.findByRole('link', { name: 'Download your library' });
    expect(link).toHaveAttribute('href', '/api/account/export');
    expect(link).toHaveAttribute('download');
  });

  it('asks for the password again before deleting the account, then signs out', async () => {
    let sent: unknown = null;
    server.use(
      http.delete('/api/account', async ({ request }) => {
        sent = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderAccount();

    await userEvent.click(await screen.findByRole('button', { name: 'Delete account' }));
    await userEvent.type(screen.getByLabelText('Your password, to confirm'), 'password1234');
    await userEvent.click(screen.getByRole('button', { name: 'Delete my account' }));

    expect(await screen.findByText('Sign-in page')).toBeInTheDocument();
    expect(sent).toEqual({ password: 'password1234' });
  });

  it('keeps the account when the password is wrong, and says why', async () => {
    server.use(
      http.delete('/api/account', () =>
        HttpResponse.json(
          {
            error: 'validation_failed',
            message: 'Please check the highlighted fields',
            details: { password: 'That isn’t your current password' },
          },
          { status: 400 },
        ),
      ),
    );
    renderAccount();

    await userEvent.click(await screen.findByRole('button', { name: 'Delete account' }));
    await userEvent.type(screen.getByLabelText('Your password, to confirm'), 'nope');
    await userEvent.click(screen.getByRole('button', { name: 'Delete my account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('That isn’t your current password');
    expect(screen.queryByText('Sign-in page')).not.toBeInTheDocument();
  });
});
