SET search_path TO amcservice;

CREATE TABLE tenant_automation_settings(
 tenant_id uuid PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
 purchase_reminder_days int NOT NULL DEFAULT 15 CHECK(purchase_reminder_days BETWEEN 1 AND 365),
 premium_customer_threshold numeric(12,2) NOT NULL DEFAULT 10000 CHECK(premium_customer_threshold >= 0),
 automatic_notifications boolean NOT NULL DEFAULT false,
 whatsapp_enabled boolean NOT NULL DEFAULT false,
 whatsapp_number varchar(20),
 updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO tenant_automation_settings(tenant_id)
SELECT id FROM tenants ON CONFLICT DO NOTHING;

CREATE INDEX customers_tenant_lifetime_idx ON customers(tenant_id,lifetime_value DESC);
