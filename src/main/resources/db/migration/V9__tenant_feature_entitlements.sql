SET search_path TO amcservice;

CREATE TABLE tenant_feature_entitlements(
 tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 feature_code varchar(50) NOT NULL,
 enabled boolean NOT NULL DEFAULT false,
 configuration jsonb NOT NULL DEFAULT '{}',
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id, feature_code)
);

INSERT INTO tenant_feature_entitlements(tenant_id,feature_code,enabled)
SELECT id,'FACEBOOK_SOCIAL',false FROM tenants ON CONFLICT DO NOTHING;
