import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ApiError } from './api/client';
import { AuthProvider } from './auth/AuthProvider';
import RequireAuth from './auth/RequireAuth';
import AppShell from './components/AppShell';
import AddPage from './pages/AddPage';
import ForYouPage from './pages/ForYouPage';
import ItemPage from './pages/ItemPage';
import LibraryPage from './pages/LibraryPage';
import SignInPage from './pages/SignInPage';
import SignUpPage from './pages/SignUpPage';
import StatsPage from './pages/StatsPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      // A 4xx won't change on a second try, and retrying a 401 only delays the
      // trip back to the sign-in page.
      retry: (failureCount, error) =>
        failureCount < 1 && !(error instanceof ApiError && error.status < 500),
      staleTime: 30 * 1000,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/signin" element={<SignInPage />} />
            <Route path="/signup" element={<SignUpPage />} />
            <Route element={<RequireAuth />}>
              <Route element={<AppShell />}>
                <Route path="/" element={<LibraryPage />} />
                <Route path="/add" element={<AddPage />} />
                <Route path="/for-you" element={<ForYouPage />} />
                <Route path="/stats" element={<StatsPage />} />
                <Route path="/item/:id" element={<ItemPage />} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
