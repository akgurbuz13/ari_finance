-- V017: Relocate pg_trgm extension to shared schema so similarity() is findable
-- when Flyway sets search_path to identity,ledger,payments,shared (excluding public).
--
-- The extension was originally created in V008 without a SCHEMA clause, which
-- installs it into 'public'. But the application's search_path (controlled by
-- Flyway schemas config) doesn't include 'public', so similarity() calls fail
-- with "function similarity(text, text) does not exist".

-- Drop from public if it exists there
DROP EXTENSION IF EXISTS pg_trgm;

-- Recreate in shared schema (which is in the search_path)
CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA shared;
