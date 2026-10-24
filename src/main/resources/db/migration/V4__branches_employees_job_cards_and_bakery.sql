SET search_path TO amcservice;

ALTER TABLE tenants ADD COLUMN domain varchar(30) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE tenants ADD COLUMN theme_key varchar(40) NOT NULL DEFAULT 'service-green';

CREATE TABLE branches(
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 code varchar(40) NOT NULL, name varchar(150) NOT NULL, address text, phone varchar(20), active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(tenant_id,code)
);

CREATE TABLE employees(
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 branch_id uuid NOT NULL REFERENCES branches(id), user_id uuid UNIQUE REFERENCES users(id),
 name varchar(150) NOT NULL, employee_type varchar(20) NOT NULL CHECK(employee_type IN('OWNER','USER')),
 job_role varchar(30) NOT NULL CHECK(job_role IN('OWNER','MANAGER','COUNTER','CHEF','DELIVERY','SERVICE')),
 date_of_joining date NOT NULL, hourly_rate numeric(12,2), monthly_salary numeric(12,2), active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE users ADD COLUMN branch_id uuid REFERENCES branches(id);
ALTER TABLE users ADD COLUMN employee_id uuid REFERENCES employees(id);
ALTER TABLE orders ADD COLUMN branch_id uuid REFERENCES branches(id);

CREATE TABLE order_reference_images(
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE, file_name varchar(255) NOT NULL,
 content_type varchar(100) NOT NULL, file_size bigint NOT NULL, image_data bytea NOT NULL,
 uploaded_by uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE job_cards(
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 branch_id uuid NOT NULL REFERENCES branches(id), order_id uuid REFERENCES orders(id),
 chef_employee_id uuid REFERENCES employees(id), title varchar(200) NOT NULL, notes text,
 due_at timestamptz, priority varchar(20) NOT NULL DEFAULT 'NORMAL' CHECK(priority IN('NORMAL','HIGH','URGENT')),
 status varchar(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN('OPEN','ASSIGNED','IN_PROGRESS','READY','COMPLETED','CANCELLED')),
 created_by uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX branches_tenant_idx ON branches(tenant_id);
CREATE INDEX employees_tenant_branch_idx ON employees(tenant_id,branch_id);
CREATE INDEX reference_images_order_idx ON order_reference_images(tenant_id,order_id);
CREATE INDEX job_cards_tenant_status_idx ON job_cards(tenant_id,status);

INSERT INTO tenants(id,code,name,domain,theme_key) VALUES
('10000000-0000-0000-0000-000000000010','BAKESNCAKES','Bakes n Cakes','BAKERY','bakery-rose');

INSERT INTO branches(id,tenant_id,code,name,address) VALUES
('11000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000010','LOHAGAON','Lohagaon','Lohagaon, Pune'),
('11000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000010','CHANDANNAGAR','Chandannagar','Chandannagar, Pune');

INSERT INTO users(id,tenant_id,branch_id,username,password_hash,display_name,role) VALUES
('20000000-0000-0000-0000-000000000010','10000000-0000-0000-0000-000000000010','11000000-0000-0000-0000-000000000001','bakes.owner','{bootstrap}ChangeMe@123','Bakes n Cakes Owner','OWNER');

INSERT INTO employees(id,tenant_id,branch_id,user_id,name,employee_type,job_role,date_of_joining,monthly_salary) VALUES
('21000000-0000-0000-0000-000000000010','10000000-0000-0000-0000-000000000010','11000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000010','Bakes n Cakes Owner','OWNER','OWNER',current_date,0);
UPDATE users SET employee_id='21000000-0000-0000-0000-000000000010' WHERE id='20000000-0000-0000-0000-000000000010';

INSERT INTO catalog_items(id,tenant_id,type,name,description,price) VALUES
('30000000-0000-0000-0000-000000000010','10000000-0000-0000-0000-000000000010','PRODUCT','Celebration Cake','Custom flavour, finish and message',850),
('30000000-0000-0000-0000-000000000011','10000000-0000-0000-0000-000000000010','PRODUCT','Designer Cake','Theme-based handcrafted cake',1600),
('30000000-0000-0000-0000-000000000012','10000000-0000-0000-0000-000000000010','PRODUCT','Cupcake Box','Box of six handcrafted cupcakes',450);
