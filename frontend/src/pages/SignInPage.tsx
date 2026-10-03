import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/context';
import AuthCard from '../components/AuthCard';
import Button from '../components/Button';

export default function SignInPage() {
  const { user, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) {
    return <Navigate to="/" replace />;
  }

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signIn(username, password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from ?? '/', { replace: true });
    } catch (caught) {
      setError(
        caught instanceof ApiError ? caught.message : 'Could not reach the server. Try again.',
      );
      setBusy(false);
    }
  };

  return (
    <AuthCard
      heading="Sign in"
      intro="Pick up your library where you left it."
      footer={
        <>
          No account yet?{' '}
          <Link to="/signup" className="text-lamp underline underline-offset-4">
            Create one
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
            autoFocus
            required
          />
        </label>

        <label className="flex flex-col gap-1.5 text-sm">
          Password
          <input
            className="field"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
          />
        </label>

        {error ? (
          <p role="alert" className="text-sm text-ember">
            {error}
          </p>
        ) : null}

        <Button type="submit" disabled={busy}>
          {busy ? 'Signing in...' : 'Sign in'}
        </Button>
      </form>
    </AuthCard>
  );
}
