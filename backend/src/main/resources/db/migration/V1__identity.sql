CREATE TABLE organizations (
    id uuid PRIMARY KEY,
    name varchar(160) NOT NULL CHECK (length(trim(name)) > 0)
);

CREATE TABLE portal_users (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    email varchar(254) NOT NULL UNIQUE,
    display_name varchar(120) NOT NULL CHECK (length(trim(display_name)) > 0),
    role varchar(24) NOT NULL CHECK (role IN ('CLIENT_ADMIN', 'MEMBER')),
    password_hash varchar(255) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT portal_users_normalized_email CHECK (email = lower(trim(email)) AND length(email) > 0)
);

CREATE INDEX portal_users_organization_idx ON portal_users(organization_id);
