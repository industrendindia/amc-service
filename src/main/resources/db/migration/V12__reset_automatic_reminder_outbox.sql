SET search_path TO amcservice;

-- Rebuild automatic reminders using the corrected tenant window rules.
-- Manual messages, OTPs and final-bill jobs are intentionally preserved.
DELETE FROM notification_jobs
WHERE status = 'PENDING'
  AND kind IN ('BIRTHDAY','ANNIVERSARY','EXPIRY','PURCHASE_REMINDER');
