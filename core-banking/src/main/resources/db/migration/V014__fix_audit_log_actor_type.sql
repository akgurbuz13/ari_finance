-- Fix audit_log actor_type constraint to accept both lowercase and uppercase values
-- The application may send either 'USER' or 'user' depending on context

ALTER TABLE shared.audit_log DROP CONSTRAINT IF EXISTS chk_actor_type;

ALTER TABLE shared.audit_log ADD CONSTRAINT chk_actor_type
  CHECK (LOWER(actor_type) IN ('user', 'admin', 'system'));
