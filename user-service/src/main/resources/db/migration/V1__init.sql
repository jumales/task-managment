CREATE TABLE users (
    id         UUID                     PRIMARY KEY,
    name       VARCHAR(255)             NOT NULL,
    email      VARCHAR(255)             NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE roles (
    id          UUID                     PRIMARY KEY,
    name        VARCHAR(255)             NOT NULL,
    description TEXT,
    deleted_at  TIMESTAMP WITH TIME ZONE
);

-- Partial unique index: name must be unique among non-deleted roles
CREATE UNIQUE INDEX idx_roles_name_active ON roles (name) WHERE deleted_at IS NULL;

CREATE TABLE rights (
    id          UUID                     PRIMARY KEY,
    name        VARCHAR(255)             NOT NULL,
    description TEXT,
    deleted_at  TIMESTAMP WITH TIME ZONE
);

-- Partial unique index: name must be unique among non-deleted rights
CREATE UNIQUE INDEX idx_rights_name_active ON rights (name) WHERE deleted_at IS NULL;

CREATE TABLE user_roles (
    id          UUID                     PRIMARY KEY,
    user_id     UUID                     NOT NULL REFERENCES users (id),
    role_id     UUID                     NOT NULL REFERENCES roles (id),
    assigned_at TIMESTAMP                NOT NULL,
    assigned_by VARCHAR(255),
    deleted_at  TIMESTAMP WITH TIME ZONE
);

-- Partial unique index: a user can only have each role once while active
CREATE UNIQUE INDEX idx_user_roles_active ON user_roles (user_id, role_id) WHERE deleted_at IS NULL;

CREATE TABLE role_rights (
    id          UUID                     PRIMARY KEY,
    role_id     UUID                     NOT NULL REFERENCES roles (id),
    right_id    UUID                     NOT NULL REFERENCES rights (id),
    granted_at  TIMESTAMP                NOT NULL,
    granted_by  VARCHAR(255),
    deleted_at  TIMESTAMP WITH TIME ZONE
);

-- Partial unique index: a role can only have each right once while active
CREATE UNIQUE INDEX idx_role_rights_active ON role_rights (role_id, right_id) WHERE deleted_at IS NULL;
