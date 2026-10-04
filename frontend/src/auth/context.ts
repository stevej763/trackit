import { createContext, useContext } from 'react';
import type { User } from '../api/types';

export interface AuthState {
  user: User | null;
  /** True only while the very first "who am I" call is in flight. */
  isLoading: boolean;
  signIn: (username: string, password: string) => Promise<void>;
  signUp: (username: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  /** Deletes the account for good, then behaves like signing out. */
  deleteAccount: (password: string) => Promise<void>;
}

export const AuthContext = createContext<AuthState | null>(null);

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
