SET search_path TO amcservice;
ALTER TABLE custom_cake_requests ADD COLUMN order_id uuid REFERENCES orders(id);
ALTER TABLE custom_cake_requests ADD COLUMN quoted_by uuid REFERENCES users(id);
ALTER TABLE custom_cake_requests ADD COLUMN quoted_at timestamptz;
CREATE UNIQUE INDEX custom_cake_requests_order_idx ON custom_cake_requests(order_id) WHERE order_id IS NOT NULL;
