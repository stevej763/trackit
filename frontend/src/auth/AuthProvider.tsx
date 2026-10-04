import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, api } from '../api/client';
import { queryKeys } from '../api/hooks';
import type { User } from '../api/types';
import { AuthContext, type AuthState } from './context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);

  // A 401 here is the normal "not signed in" answer, not a failure to retry.
  const { isLoading } = useQuery({
    queryKey: queryKeys.me,
    queryFn: async () => {
      try {
        const me = await api.me();
        setUser(me);
        return me;
      } catch {
        setUser(null);
        return null;
      }
    },
    retry: false,
    staleTime: Infinity,
  });

  // The session can end under a page that's already open: it expires, or the
  // user signs out in another tab. Any 401 from then on means "signed out", so
  // drop the user and let RequireAuth send them to sign in, rather than leave
  // each page showing "Please sign in" as an error. Sign-in's own 401s don't
  // pass through here: those calls aren't queries or mutations.
  useEffect(() => {
    const onError = (error: unknown) => {
      if (error instanceof ApiError && error.status === 401) {
        setUser(null);
        queryClient.clear();
      }
    };
    const unsubscribeQueries = queryClient.getQueryCache().subscribe((event) => {
      if (event.type === 'updated' && event.action.type === 'error') {
        onError(event.action.error);
      }
    });
    const unsubscribeMutations = queryClient.getMutationCache().subscribe((event) => {
      if (event.type === 'updated' && event.action.type === 'error') {
        onError(event.action.error);
      }
    });
    return () => {
      unsubscribeQueries();
      unsubscribeMutations();
    };
  }, [queryClient]);

  const signIn = useCallback(
    async (username: string, password: string) => {
      setUser(await api.login(username, password));
      queryClient.clear();
    },
    [queryClient],
  );

  const signUp = useCallback(
    async (username: string, password: string) => {
      setUser(await api.register(username, password));
      queryClient.clear();
    },
    [queryClient],
  );

  const signOut = useCallback(async () => {
    await api.logout();
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  const deleteAccount = useCallback(
    async (password: string) => {
      await api.deleteAccount(password);
      setUser(null);
      queryClient.clear();
    },
    [queryClient],
  );

  const value = useMemo<AuthState>(
    () => ({ user, isLoading, signIn, signUp, signOut, deleteAccount }),
    [user, isLoading, signIn, signUp, signOut, deleteAccount],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
