SET search_path TO amcservice;

ALTER TABLE tenants ADD COLUMN domain_prefix varchar(8);
ALTER TABLE tenants ADD COLUMN document_code varchar(12);
ALTER TABLE branches ADD COLUMN document_code varchar(8);
ALTER TABLE job_cards ADD COLUMN job_number varchar(50);

UPDATE tenants
SET domain_prefix = CASE WHEN domain = 'BAKERY' THEN 'CS' ELSE 'AMC' END,
    document_code = upper(left(regexp_replace(code, '[^A-Za-z0-9]', '', 'g'), 3));

UPDATE tenants SET document_code = 'BNC' WHERE code = 'BAKESNCAKES';

WITH ranked AS (
  SELECT id,
         upper(left(regexp_replace(code, '[^A-Za-z0-9]', '', 'g'), 1)) AS base_code,
         row_number() OVER (
           PARTITION BY tenant_id, upper(left(regexp_replace(code, '[^A-Za-z0-9]', '', 'g'), 1))
           ORDER BY created_at, id
         ) AS code_rank,
         count(*) OVER (
           PARTITION BY tenant_id, upper(left(regexp_replace(code, '[^A-Za-z0-9]', '', 'g'), 1))
         ) AS code_count
  FROM branches
)
UPDATE branches b
SET document_code = ranked.base_code || CASE WHEN ranked.code_count > 1 THEN ranked.code_rank::text ELSE '' END
FROM ranked WHERE ranked.id = b.id;

ALTER TABLE tenants ALTER COLUMN domain_prefix SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN document_code SET NOT NULL;
ALTER TABLE branches ALTER COLUMN document_code SET NOT NULL;
ALTER TABLE tenants ADD CONSTRAINT tenants_domain_prefix_format CHECK(domain_prefix ~ '^[A-Z0-9]{2,8}$');
ALTER TABLE tenants ADD CONSTRAINT tenants_document_code_format CHECK(document_code ~ '^[A-Z0-9]{2,12}$');
ALTER TABLE branches ADD CONSTRAINT branches_document_code_format CHECK(document_code ~ '^[A-Z0-9]{1,8}$');
ALTER TABLE branches ADD CONSTRAINT branches_tenant_document_code_unique UNIQUE(tenant_id, document_code);

CREATE SEQUENCE business_document_number_seq START WITH 123456;

UPDATE job_cards j
SET job_number = t.domain_prefix || '-' || t.document_code || b.document_code || '-J-' ||
                 lpad(nextval('business_document_number_seq')::text, 6, '0')
FROM tenants t, branches b
WHERE t.id = j.tenant_id AND b.id = j.branch_id;

ALTER TABLE job_cards ALTER COLUMN job_number SET NOT NULL;
ALTER TABLE job_cards ADD CONSTRAINT job_cards_job_number_unique UNIQUE(job_number);
