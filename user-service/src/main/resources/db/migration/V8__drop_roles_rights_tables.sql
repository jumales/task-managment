-- Drop roles and rights tables; authorization is handled entirely by Keycloak JWT claims.
-- Drop in FK-safe order: dependents first, then referenced tables.
DROP TABLE IF EXISTS role_rights;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS rights;
