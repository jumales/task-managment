import Keycloak from 'keycloak-js';

/**
 * Singleton Keycloak instance configured from environment variables.
 * Initialized once in main.tsx before the React tree is rendered.
 */
const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});

export default keycloak;
