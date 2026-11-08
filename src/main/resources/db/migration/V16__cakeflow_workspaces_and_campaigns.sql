SET search_path TO amcservice;

CREATE TABLE custom_cake_requests(
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  branch_id uuid REFERENCES branches(id),
  customer_id uuid REFERENCES customers(id),
  customer_name varchar(160) NOT NULL,
  customer_phone varchar(24) NOT NULL,
  occasion varchar(100),
  required_at timestamptz,
  servings integer CHECK(servings IS NULL OR servings > 0),
  flavour varchar(120),
  preparation varchar(80),
  budget numeric(12,2) CHECK(budget IS NULL OR budget >= 0),
  design_notes text,
  reference_file_name varchar(255),
  reference_content_type varchar(120),
  reference_data bytea,
  status varchar(30) NOT NULL DEFAULT 'QUOTE_REQUESTED'
    CHECK(status IN('QUOTE_REQUESTED','QUOTED','APPROVED','CONVERTED','DECLINED')),
  quoted_price numeric(12,2),
  created_by uuid NOT NULL REFERENCES users(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX custom_cake_requests_tenant_created_idx ON custom_cake_requests(tenant_id,created_at DESC);

CREATE TABLE whatsapp_campaigns(
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name varchar(160) NOT NULL,
  audience_type varchar(30) NOT NULL CHECK(audience_type IN('BIRTHDAY','ANNIVERSARY','MARKETING_CONSENT','ALL_CONSENTED')),
  event_window_days integer NOT NULL DEFAULT 7 CHECK(event_window_days BETWEEN 0 AND 90),
  message text NOT NULL,
  scheduled_for timestamptz,
  status varchar(30) NOT NULL DEFAULT 'DRAFT' CHECK(status IN('DRAFT','QUEUED','SENT','PARTIAL','FAILED')),
  audience_count integer NOT NULL DEFAULT 0,
  sent_count integer NOT NULL DEFAULT 0,
  failure_reason varchar(500),
  created_by uuid NOT NULL REFERENCES users(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX whatsapp_campaigns_tenant_created_idx ON whatsapp_campaigns(tenant_id,created_at DESC);

INSERT INTO tenant_feature_entitlements(tenant_id,feature_code,enabled)
SELECT t.id,f.feature_code,(t.domain='BAKERY')
FROM tenants t
CROSS JOIN (VALUES
 ('NAV_SALES'),('NAV_CAKE_CATALOGUE'),('NAV_MANAGE_CAKES'),
 ('NAV_CUSTOMERS'),('NAV_ORDERS_BILLING'),('NAV_KITCHEN_BOARD'),('NAV_WHATSAPP_CAMPAIGNS')
) f(feature_code)
ON CONFLICT(tenant_id,feature_code) DO UPDATE SET enabled=CASE
 WHEN tenant_feature_entitlements.enabled THEN true ELSE excluded.enabled END;
