-- Performance indexes for N+1-free batch loading

-- user_roles: batch-load all roles for a set of users (findByUserIn)
CREATE INDEX idx_user_roles_user_id  ON user_roles (user_id)  WHERE deleted_at IS NULL;

-- user_roles: check / revoke roles by role (existsByRole, revokeRole)
CREATE INDEX idx_user_roles_role_id  ON user_roles (role_id)  WHERE deleted_at IS NULL;

-- role_rights: batch-load all rights for a set of roles (findByRoleIn)
CREATE INDEX idx_role_rights_role_id ON role_rights (role_id) WHERE deleted_at IS NULL;
