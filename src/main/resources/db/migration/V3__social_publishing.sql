SET search_path TO amcservice;

CREATE TABLE social_oauth_states (
    state varchar(120) PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE social_connections (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    platform varchar(20) NOT NULL CHECK (platform IN ('FACEBOOK','INSTAGRAM')),
    external_account_id varchar(120) NOT NULL,
    external_account_name varchar(200) NOT NULL,
    encrypted_access_token text NOT NULL,
    token_expires_at timestamptz,
    connected_by uuid NOT NULL REFERENCES users(id),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, platform)
);

CREATE TABLE social_posts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    connection_id uuid NOT NULL REFERENCES social_connections(id),
    platform varchar(20) NOT NULL,
    media_type varchar(20) NOT NULL CHECK (media_type IN ('IMAGES','VIDEO')),
    caption text,
    media_count int NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('PUBLISHING','PUBLISHED','FAILED')),
    external_post_id varchar(200),
    error_message text,
    created_by uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
);

CREATE INDEX social_connections_tenant_idx ON social_connections(tenant_id,platform);
CREATE INDEX social_posts_tenant_created_idx ON social_posts(tenant_id,created_at DESC);
CREATE INDEX social_oauth_states_expiry_idx ON social_oauth_states(expires_at);
