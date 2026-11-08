SET search_path TO amcservice;
ALTER TABLE catalog_items ADD COLUMN catalog_group varchar(20) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE catalog_items ADD CONSTRAINT catalog_items_group_check CHECK(catalog_group IN('CAKE','GENERAL'));
UPDATE catalog_items ci SET catalog_group='CAKE' FROM tenants t WHERE t.id=ci.tenant_id AND t.domain='BAKERY' AND ci.type='PRODUCT';
CREATE INDEX catalog_items_tenant_group_idx ON catalog_items(tenant_id,catalog_group,type,active);
