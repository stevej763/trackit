import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/context';

const NAV = [
  { to: '/', label: 'Library' },
  { to: '/add', label: 'Add' },
  { to: '/for-you', label: 'For you' },
  { to: '/stats', label: 'Stats' },
];

export default function AppShell() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  const handleSignOut = async () => {
    await signOut();
    navigate('/signin', { replace: true });
  };

  return (
    <div className="min-h-dvh bg-ink">
      <header className="border-b border-edge">
        <div className="mx-auto flex max-w-[1400px] flex-wrap items-center gap-x-8 gap-y-3 px-4 py-4 sm:px-8">
          <Link to="/" className="font-display text-2xl tracking-tight">
            track<span className="text-lamp">it</span>
          </Link>

          <nav className="flex gap-6" aria-label="Main">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) =>
                  `border-b-2 pb-0.5 text-sm ${
                    isActive
                      ? 'border-lamp text-paper'
                      : 'border-transparent text-paper-dim hover:text-paper'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-4 text-sm">
            <NavLink
              to="/account"
              className={({ isActive }) =>
                isActive ? 'text-paper' : 'text-paper-dim hover:text-paper'
              }
              aria-label={`Your account (${user?.username ?? ''})`}
            >
              {user?.username}
            </NavLink>
            <button
              type="button"
              onClick={handleSignOut}
              className="text-paper-dim underline decoration-edge-bright underline-offset-4 hover:text-paper"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-[1400px] px-4 py-8 sm:px-8">
        <Outlet />
      </main>
    </div>
  );
}
