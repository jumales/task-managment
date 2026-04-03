import { jsx as _jsx } from "react/jsx-runtime";
import { createContext, useContext, useEffect } from 'react';
import keycloak from './keycloak';
const AuthContext = createContext(null);
/**
 * Provides auth context to the React tree.
 * Keycloak is already initialized by the time this renders (done in main.tsx).
 * Sets up a background token refresh interval.
 */
export function AuthProvider({ children }) {
    useEffect(() => {
        // Proactively refresh the token 30 seconds before it expires
        const interval = setInterval(() => keycloak.updateToken(30), 25000);
        return () => clearInterval(interval);
    }, []);
    return (_jsx(AuthContext.Provider, { value: {
            name: keycloak.tokenParsed?.name ?? keycloak.tokenParsed?.preferred_username ?? '',
            username: keycloak.tokenParsed?.preferred_username ?? '',
            isAdmin: (keycloak.tokenParsed?.realm_access?.roles ?? []).includes('ADMIN'),
            logout: () => keycloak.logout(),
        }, children: children }));
}
/** Returns the current auth context. Must be used inside AuthProvider. */
export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx)
        throw new Error('useAuth must be used inside AuthProvider');
    return ctx;
}
