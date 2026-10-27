SET search_path TO amcservice;

ALTER TABLE tenants
 ADD COLUMN logo_file_name varchar(255),
 ADD COLUMN logo_content_type varchar(100),
 ADD COLUMN logo_data bytea;

INSERT INTO tenant_feature_entitlements(tenant_id,feature_code,enabled)
SELECT id,'WHATSAPP',false FROM tenants ON CONFLICT DO NOTHING;

CREATE INDEX notification_jobs_tenant_schedule_idx
 ON notification_jobs(tenant_id,scheduled_for,status);
