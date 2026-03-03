-- V017: Relocate pg_trgm extension to shared schema so similarity() is findable
-- when Flyway sets search_path to identity,ledger,payments,shared (excluding public).
--
-- The extension was originally created in V008 without a SCHEMA clause, which
-- installs it into 'public'. But the application's search_path (controlled by
-- Flyway schemas config) doesn't include 'public', so similarity() calls fail
-- with "function similarity(text, text) does not exist".
--
-- ALTER EXTENSION SET SCHEMA relocates the extension and all its objects
-- (functions, operators, operator classes) without dropping dependent objects
-- like the GIN index on sanctions_list.

ALTER EXTENSION pg_trgm SET SCHEMA shared;
