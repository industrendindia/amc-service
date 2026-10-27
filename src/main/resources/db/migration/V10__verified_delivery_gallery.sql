SET search_path TO amcservice;

CREATE TABLE order_delivery_otps(
 order_id uuid PRIMARY KEY REFERENCES orders(id) ON DELETE CASCADE,
 tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 otp_hash varchar(64) NOT NULL,
 whatsapp_opt_out boolean NOT NULL DEFAULT false,
 attempts int NOT NULL DEFAULT 0,
 expires_at timestamptz NOT NULL,
 verified_at timestamptz,
 requested_by uuid NOT NULL REFERENCES users(id),
 created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE order_delivery_media(
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
 file_name varchar(255) NOT NULL,
 content_type varchar(100) NOT NULL,
 file_size bigint NOT NULL,
 media_data bytea NOT NULL,
 favorite boolean NOT NULL DEFAULT false,
 uploaded_by uuid NOT NULL REFERENCES users(id),
 created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX delivery_media_tenant_favorite_idx ON order_delivery_media(tenant_id,favorite DESC,created_at DESC);
