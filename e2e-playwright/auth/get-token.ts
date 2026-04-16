import { APIRequestContext } from '@playwright/test';

const KEYCLOAK_TOKEN_URL =
  'http://localhost:8180/realms/demo/protocol/openid-connect/token';

/**
 * Fetches a Keycloak access token for a given user via the Resource Owner
 * Password Credentials grant (e2e-client with directAccessGrantsEnabled=true).
 *
 * Use this for direct API assertions where a Bearer token is needed without
 * a browser context (e.g., verifying that a SUPERVISOR POST returns 403).
 */
export async function getToken(
  request: APIRequestContext,
  username: string,
  password: string
): Promise<string> {
  const response = await request.post(KEYCLOAK_TOKEN_URL, {
    form: {
      grant_type: 'password',
      client_id: 'e2e-client',
      client_secret: 'e2e-secret',
      username,
      password,
    },
  });

  if (!response.ok()) {
    throw new Error(
      `Failed to fetch token for ${username}: ${response.status()} ${await response.text()}`
    );
  }

  const body = await response.json() as { access_token: string };
  return body.access_token;
}
