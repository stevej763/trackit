import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './context';

export default function RequireAuth() {
  const { user, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    // Nothing to say yet, and a flash of the login page would be worse.
    return <div className="min-h-dvh bg-ink" aria-busy="true" />;
  }

  if (!user) {
    return <Navigate to="/signin" replace state={{ from: location.pathname + location.search }} />;
  }

  return <Outlet />;
}
