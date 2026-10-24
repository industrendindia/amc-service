SET search_path TO amcservice;

ALTER TABLE orders ADD COLUMN due_at timestamptz;
ALTER TABLE orders ADD COLUMN completed_at timestamptz;
ALTER TABLE orders ADD COLUMN delivered_at timestamptz;
ALTER TABLE orders ADD COLUMN fulfillment_status varchar(20) NOT NULL DEFAULT 'NEW';
ALTER TABLE orders ADD COLUMN payment_status varchar(20) NOT NULL DEFAULT 'PAID';
ALTER TABLE orders ADD COLUMN amount_paid numeric(12,2) NOT NULL DEFAULT 0;
UPDATE orders SET amount_paid=total;
ALTER TABLE orders DROP CONSTRAINT orders_payment_method_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_method_check CHECK(payment_method IN('UPI','CASH','ZERO','PENDING'));
ALTER TABLE orders ADD CONSTRAINT orders_fulfillment_status_check CHECK(fulfillment_status IN('NEW','IN_PROGRESS','READY','COMPLETED','DELIVERED','CANCELLED'));
ALTER TABLE orders ADD CONSTRAINT orders_payment_status_check CHECK(payment_status IN('PENDING','PARTIAL','PAID','REFUNDED'));

CREATE TABLE order_payments(
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
 amount numeric(12,2) NOT NULL CHECK(amount>=0), method varchar(20) NOT NULL CHECK(method IN('UPI','CASH','ZERO')),
 reference_number varchar(150), received_by uuid NOT NULL REFERENCES users(id), paid_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE job_card_chefs(
 job_card_id uuid NOT NULL REFERENCES job_cards(id) ON DELETE CASCADE,
 employee_id uuid NOT NULL REFERENCES employees(id), assigned_by uuid NOT NULL REFERENCES users(id),
 assigned_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(job_card_id,employee_id)
);

INSERT INTO job_card_chefs(job_card_id,employee_id,assigned_by)
SELECT id,chef_employee_id,created_by FROM job_cards WHERE chef_employee_id IS NOT NULL;
ALTER TABLE job_cards DROP COLUMN chef_employee_id;

CREATE TABLE job_card_notes(
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 job_card_id uuid NOT NULL REFERENCES job_cards(id) ON DELETE CASCADE,
 author_employee_id uuid REFERENCES employees(id), author_user_id uuid NOT NULL REFERENCES users(id),
 note text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX orders_tenant_bill_search_idx ON orders(tenant_id,bill_number);
CREATE INDEX payments_order_idx ON order_payments(tenant_id,order_id,paid_at);
CREATE INDEX job_card_chefs_employee_idx ON job_card_chefs(employee_id);
CREATE INDEX job_card_notes_job_idx ON job_card_notes(tenant_id,job_card_id,created_at);
