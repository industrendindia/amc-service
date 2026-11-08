SET search_path TO amcservice;

ALTER TABLE job_cards ADD COLUMN order_item_id uuid REFERENCES order_items(id) ON DELETE SET NULL;
ALTER TABLE job_cards DROP CONSTRAINT IF EXISTS job_cards_status_check;
ALTER TABLE job_cards ADD CONSTRAINT job_cards_status_check
 CHECK(status IN('OPEN','ASSIGNED','IN_PROGRESS','QUALITY_CHECK','READY','COMPLETED','CANCELLED'));

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_fulfillment_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_fulfillment_status_check
 CHECK(fulfillment_status IN('NEW','IN_PROGRESS','QUALITY_CHECK','READY','COMPLETED','DELIVERED','CANCELLED'));

CREATE TABLE job_card_quality_checks(
 job_card_id uuid PRIMARY KEY REFERENCES job_cards(id) ON DELETE CASCADE,
 tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 weight_checked boolean NOT NULL DEFAULT false,
 flavour_checked boolean NOT NULL DEFAULT false,
 spelling_checked boolean NOT NULL DEFAULT false,
 appearance_checked boolean NOT NULL DEFAULT false,
 result varchar(20) NOT NULL CHECK(result IN('PASSED','REWORK')),
 notes text,
 checked_by uuid NOT NULL REFERENCES users(id),
 checked_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE job_card_final_photos(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 job_card_id uuid NOT NULL REFERENCES job_cards(id) ON DELETE CASCADE,
 tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 file_name varchar(255) NOT NULL,
 content_type varchar(120) NOT NULL,
 file_size bigint NOT NULL,
 image_data bytea NOT NULL,
 uploaded_by uuid NOT NULL REFERENCES users(id),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX job_card_final_photos_job_idx ON job_card_final_photos(tenant_id,job_card_id,created_at DESC);
