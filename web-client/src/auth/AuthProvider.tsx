import { createContext, useContext, useEffect } from 'react';
import keycloak from './keycloak';

interface AuthContextValue {
  /** Display name from the Keycloak token. */
  username: string;
  /** True when the current user has the ADMIN realm role in Keycloak. */
  isAdmin: boolean;
  /** Logs out and redirects to the Keycloak logout page. */
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Provides auth context to the React tree.
 * Keycloak is already initialized by the time this renders (done in main.tsx).
 * Sets up a background token refresh interval.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  useEffect(() => {
    // Proactively refresh the token 30 seconds before it expires
    const interval = setInterval(() => keycloak.updateToken(30), 25_000);
    return () => clearInterval(interval);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        username: keycloak.tokenParsed?.preferred_username ?? '',
        isAdmin: (keycloak.tokenParsed?.realm_access?.roles ?? []).includes('ADMIN'),
        logout: () => keycloak.logout(),
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

/** Returns the current auth context. Must be used inside AuthProvider. */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
