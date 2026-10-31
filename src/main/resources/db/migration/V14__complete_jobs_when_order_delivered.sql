SET search_path TO amcservice;

UPDATE job_cards j
SET status='COMPLETED',updated_at=now()
FROM orders o
WHERE j.order_id=o.id
  AND o.fulfillment_status='DELIVERED'
  AND j.status<>'COMPLETED';

CREATE OR REPLACE FUNCTION complete_delivery_job_cards()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
 IF NEW.fulfillment_status='DELIVERED'
    AND OLD.fulfillment_status IS DISTINCT FROM NEW.fulfillment_status THEN
  UPDATE job_cards
  SET status='COMPLETED',updated_at=now()
  WHERE order_id=NEW.id
    AND tenant_id=NEW.tenant_id
    AND status<>'COMPLETED';
 END IF;
 RETURN NEW;
END;
$$;

CREATE TRIGGER orders_complete_delivery_jobs
AFTER UPDATE OF fulfillment_status ON orders
FOR EACH ROW
EXECUTE FUNCTION complete_delivery_job_cards();
