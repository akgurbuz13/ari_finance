-- V017: No-op (pg_trgm stays in public schema)
-- SQL queries use explicit public.similarity() calls which works regardless
-- of search_path configuration. No extension relocation needed.
SELECT 1;
