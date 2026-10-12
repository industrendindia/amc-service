SET search_path TO amcservice;
INSERT INTO tenants(id,code,name) VALUES('10000000-0000-0000-0000-000000000001','AQUACARE','AquaCare Services');
-- One-time bootstrap markers are BCrypt-encoded by BootstrapPasswords at application startup.
INSERT INTO users(id,tenant_id,username,password_hash,display_name,role) VALUES
('20000000-0000-0000-0000-000000000001',NULL,'admin','{bootstrap}msadmin123','Platform Administrator','ADMIN'),
('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','owner','{bootstrap}owner123','Ananya Sharma','OWNER');
INSERT INTO catalog_items(id,tenant_id,type,name,description,price,duration_months) VALUES
('30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','SERVICE','AC Deep Service','Jet clean, gas and performance check',1499,3),
('30000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','PRODUCT','Premium RO Filter','High-flow sediment + carbon filter',899,12);
INSERT INTO packages(id,tenant_id,name,description,price,duration_months) VALUES
('40000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','RO Care Annual','4 scheduled visits · filters included',4499,12),
('40000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','Daily Milk Plan','30-day doorstep delivery subscription',2100,1);
