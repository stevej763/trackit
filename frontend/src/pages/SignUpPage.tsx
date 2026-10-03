import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/context';
import AuthCard from '../components/AuthCard';
import Button from '../components/Button';

export default function SignUpPage() {
  const { user, signUp } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  if (user) {
    return <Navigate to="/" replace />;
  }

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      await signUp(username, password);
      navigate('/add', { replace: true });
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFieldErrors(caught.details ?? {});
        setError(caught.details ? null : caught.message);
      } else {
        setError('Could not reach the server. Try again.');
      }
      setBusy(false);
    }
  };

  return (
    <AuthCard
      heading="Create an account"
      intro="Your library, scores and reviews are yours alone - everyone here keeps their own."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/signin" className="text-lamp underline underline-offset-4">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1.5 text-sm">
          Username
          <input
            className="field"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            aria-describedby={fieldErrors.username ? 'username-error' : undefined}
            autoFocus
            required
          />
          {fieldErrors.username ? (
            <span id="username-error" className="text-ember">
              {fieldErrors.username}
            </span>
          ) : null}
        </label>

        <label className="flex flex-col gap-1.5 text-sm">
          Password
          <input
            className="field"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
            aria-describedby={fieldErrors.password ? 'password-error' : 'password-hint'}
            required
          />
          {fieldErrors.password ? (
            <span id="password-error" className="text-ember">
              {fieldErrors.password}
            </span>
          ) : (
            <span id="password-hint" className="text-paper-dim">
              At least 8 characters.
            </span>
          )}
        </label>

        {error ? (
          <p role="alert" className="text-sm text-ember">
            {error}
          </p>
        ) : null}

        <Button type="submit" disabled={busy}>
          {busy ? 'Creating account...' : 'Create account'}
        </Button>
      </form>
    </AuthCard>
  );
}
