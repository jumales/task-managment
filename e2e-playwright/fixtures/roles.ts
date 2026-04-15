/**
 * Keycloak test user credentials and session state file paths.
 * All users are pre-configured in the demo-realm.json Keycloak import.
 */
export const USERS = {
  admin: {
    username: 'admin-user',
    password: 'admin123',
    stateFile: '.auth/admin.json',
  },
  developer: {
    username: 'dev-user',
    password: 'dev123',
    stateFile: '.auth/developer.json',
  },
  qa: {
    username: 'qa-user',
    password: 'qa123',
    stateFile: '.auth/qa.json',
  },
  devops: {
    username: 'devops-user',
    password: 'devops123',
    stateFile: '.auth/devops.json',
  },
  pm: {
    username: 'pm-user',
    password: 'pm123',
    stateFile: '.auth/pm.json',
  },
  supervisor: {
    username: 'supervisor-user',
    password: 'supervisor123',
    stateFile: '.auth/supervisor.json',
  },
} as const;

export type RoleName = keyof typeof USERS;

/** Roles that may write (create/update/delete) tasks and other resources. */
export const WRITE_ROLES: readonly RoleName[] = ['admin', 'developer', 'qa', 'devops', 'pm'];

/** Roles restricted to read-only access. */
export const READ_ONLY_ROLES: readonly RoleName[] = ['supervisor'];
