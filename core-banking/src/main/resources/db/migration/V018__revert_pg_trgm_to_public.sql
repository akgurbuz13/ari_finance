-- V018: Move pg_trgm back to public schema
-- V017 relocated pg_trgm to shared, but SQL queries now use explicit
-- public.similarity() calls which is the most reliable approach.
-- This undoes V017 so the extension lives in public where it belongs.

ALTER EXTENSION pg_trgm SET SCHEMA public;
