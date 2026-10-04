import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import { useChangePassword } from '../api/hooks';
import { useAuth } from '../auth/context';
import Button from '../components/Button';

export default function AccountPage() {
  const { user } = useAuth();

  return (
    <div className="flex max-w-xl flex-col gap-10">
      <div>
        <h1 className="text-2xl">Your account</h1>
        <p className="mt-1 text-paper-dim">Signed in as {user?.username}.</p>
      </div>

      <ChangePassword />

      <section className="border-t border-edge pt-6">
        <h2 className="text-lg">Take your library with you</h2>
        <p className="mt-2 text-paper-dim">
          Everything you&rsquo;ve tracked, with your scores, reviews and dates, as one JSON file.
        </p>
        <a
          href={api.exportUrl}
          download
          className="mt-4 inline-block rounded-md border border-edge px-4 py-2 hover:border-edge-bright"
        >
          Download your library
        </a>
      </section>

      <DeleteAccount />
    </div>
  );
}

function ChangePassword() {
  const changePassword = useChangePassword();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');

  const error = changePassword.error instanceof ApiError ? changePassword.error : null;
  const fieldErrors = error?.details ?? {};

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    changePassword.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          setCurrentPassword('');
          setNewPassword('');
        },
      },
    );
  };

  return (
    <section className="border-t border-edge pt-6">
      <h2 className="text-lg">Change your password</h2>
      <p className="mt-2 text-paper-dim">This signs you out everywhere else.</p>

      <form onSubmit={onSubmit} className="mt-4 flex flex-col gap-4">
        <label className="flex flex-col gap-1.5 text-sm">
          Current password
          <input
            className="field"
            type="password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
            autoComplete="current-password"
            aria-describedby={fieldErrors.currentPassword ? 'current-password-error' : undefined}
            required
          />
          {fieldErrors.currentPassword ? (
            <span id="current-password-error" className="text-ember">
              {fieldErrors.currentPassword}
            </span>
          ) : null}
        </label>

        <label className="flex flex-col gap-1.5 text-sm">
          New password
          <input
            className="field"
            type="password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            autoComplete="new-password"
            aria-describedby={fieldErrors.newPassword ? 'new-password-error' : 'new-password-hint'}
            required
          />
          {fieldErrors.newPassword ? (
            <span id="new-password-error" className="text-ember">
              {fieldErrors.newPassword}
            </span>
          ) : (
            <span id="new-password-hint" className="text-paper-dim">
              At least 8 characters.
            </span>
          )}
        </label>

        {changePassword.isError && !error?.details ? (
          <p role="alert" className="text-sm text-ember">
            {error ? error.message : 'Could not reach the server. Try again.'}
          </p>
        ) : null}

        <div className="flex items-center gap-4">
          <Button type="submit" disabled={changePassword.isPending}>
            {changePassword.isPending ? 'Changing...' : 'Change password'}
          </Button>
          <span role="status" className="text-sm text-paper-dim">
            {changePassword.isSuccess ? 'Password changed.' : ''}
          </span>
        </div>
      </form>
    </section>
  );
}

function DeleteAccount() {
  const { deleteAccount } = useAuth();
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await deleteAccount(password);
      navigate('/signin', { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.detail : 'Could not reach the server. Try again.');
      setBusy(false);
    }
  };

  return (
    <section className="border-t border-edge pt-6">
      <h2 className="text-lg">Delete your account</h2>
      <p className="mt-2 text-paper-dim">
        Your library, scores and reviews are deleted for good. Download them first if you might want
        them.
      </p>

      {confirming ? (
        <form onSubmit={onSubmit} className="mt-4 flex flex-col gap-4">
          <label className="flex flex-col gap-1.5 text-sm">
            Your password, to confirm
            <input
              className="field"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              autoFocus
              required
            />
          </label>

          {error ? (
            <p role="alert" className="text-sm text-ember">
              {error}
            </p>
          ) : null}

          <div className="flex gap-3">
            <Button type="submit" variant="danger" className="border border-ember" disabled={busy}>
              {busy ? 'Deleting...' : 'Delete my account'}
            </Button>
            <Button type="button" variant="quiet" onClick={() => setConfirming(false)}>
              Keep it
            </Button>
          </div>
        </form>
      ) : (
        <Button variant="danger" className="-ml-4 mt-2" onClick={() => setConfirming(true)}>
          Delete account
        </Button>
      )}
    </section>
  );
}
