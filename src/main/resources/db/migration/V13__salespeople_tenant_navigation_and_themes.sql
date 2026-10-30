SET search_path TO amcservice;

ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
 CHECK(role IN('OWNER','ADMIN','EMPLOYEE','SALESPERSON'));

INSERT INTO tenant_feature_entitlements(tenant_id,feature_code,enabled)
SELECT t.id,f.feature_code,true
FROM tenants t
CROSS JOIN (VALUES
 ('NAV_NEW_ORDER'),('NAV_BILLS'),('NAV_JOBS'),('NAV_GALLERY'),
 ('NAV_EMPLOYEES'),('NAV_CATALOG'),('NAV_REPORTS'),
 ('NAV_NOTIFICATIONS'),('NAV_INTEGRATIONS')
) AS f(feature_code)
ON CONFLICT(tenant_id,feature_code) DO NOTHING;

INSERT INTO tenant_feature_entitlements(tenant_id,feature_code,enabled)
SELECT t.id,f.feature_code,false
FROM tenants t
CROSS JOIN (VALUES ('INSTAGRAM_SOCIAL'),('AI_SOCIAL')) AS f(feature_code)
ON CONFLICT(tenant_id,feature_code) DO NOTHING;

UPDATE tenants SET theme_key='aqua-flow'
 WHERE domain='WATER_SERVICE' AND theme_key='service-green';
UPDATE tenants SET theme_key='electric-spark'
 WHERE domain='ELECTRICAL' AND theme_key='service-green';

CREATE INDEX users_platform_role_idx ON users(role,active)
 WHERE tenant_id IS NULL;
