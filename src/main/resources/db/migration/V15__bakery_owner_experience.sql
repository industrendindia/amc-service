SET search_path TO amcservice;

ALTER TABLE customers ADD COLUMN IF NOT EXISTS marketing_opt_in boolean NOT NULL DEFAULT false;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS order_updates_opt_in boolean NOT NULL DEFAULT true;

ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS category varchar(80);
ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS flavour varchar(120);
ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS unit_label varchar(40) NOT NULL DEFAULT 'item';
ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS preparation_options varchar(120);
ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS allergen_note varchar(300);
ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS image_file_name varchar(255);
ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS image_content_type varchar(120);
ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS image_data bytea;
ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS featured boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS subscription_plans(
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(40) NOT NULL UNIQUE,
  name varchar(100) NOT NULL,
  monthly_price numeric(12,2) NOT NULL CHECK(monthly_price >= 0),
  description varchar(300),
  capabilities jsonb NOT NULL DEFAULT '[]'::jsonb,
  display_order integer NOT NULL DEFAULT 0,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO subscription_plans(code,name,monthly_price,description,capabilities,display_order)
VALUES
 ('BASIC','Basic',499,'Core billing and customer management','["Orders and billing","Customer records","Catalog management"]',10),
 ('SMART','Smart',999,'Operations and customer engagement','["Everything in Basic","Kitchen or job workflow","Reports","Automated reminders"]',20),
 ('SMART_PLUS','Smart Plus',1499,'Full growth and social toolkit','["Everything in Smart","WhatsApp workflows","Social publishing","Priority support"]',30)
ON CONFLICT(code) DO NOTHING;

CREATE TABLE IF NOT EXISTS tenant_subscriptions(
  tenant_id uuid PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
  plan_id uuid NOT NULL REFERENCES subscription_plans(id),
  starts_on date NOT NULL DEFAULT current_date,
  ends_on date,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('TRIAL','ACTIVE','PAUSED','CANCELLED')),
  configured_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO tenant_feature_entitlements(tenant_id,feature_code,enabled)
SELECT t.id,f.feature_code,(t.domain='BAKERY')
FROM tenants t
CROSS JOIN (VALUES ('NAV_SALES'),('NAV_CUSTOMERS'),('NAV_CAKE_CATALOGUE'),('NAV_PLANS')) f(feature_code)
ON CONFLICT(tenant_id,feature_code) DO NOTHING;

CREATE INDEX IF NOT EXISTS catalog_items_tenant_category_idx
 ON catalog_items(tenant_id,category,active);
