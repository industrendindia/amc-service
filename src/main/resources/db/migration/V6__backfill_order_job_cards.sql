SET search_path TO amcservice;

INSERT INTO job_cards(id, tenant_id, branch_id, order_id, title, notes, due_at, priority, status, created_by, created_at)
SELECT gen_random_uuid(), o.tenant_id, o.branch_id, o.id,
       'Prepare ' || o.bill_number || ' for ' || c.name,
       'Automatically created from the customer order', o.due_at, 'NORMAL',
       CASE WHEN o.fulfillment_status = 'IN_PROGRESS' THEN 'IN_PROGRESS'
            WHEN o.fulfillment_status = 'READY' THEN 'READY'
            WHEN o.fulfillment_status IN ('COMPLETED','DELIVERED') THEN 'COMPLETED'
            WHEN o.fulfillment_status = 'CANCELLED' THEN 'CANCELLED'
            ELSE 'OPEN' END,
       o.created_by, o.created_at
FROM orders o
JOIN customers c ON c.id = o.customer_id
WHERE o.branch_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM job_cards j WHERE j.order_id = o.id);

