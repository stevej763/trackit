import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
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

  const value = useMemo<AuthState>(
    () => ({ user, isLoading, signIn, signUp, signOut }),
    [user, isLoading, signIn, signUp, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
