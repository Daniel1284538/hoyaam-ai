-- Litigation Agent Phase 1 — extensions needed for hybrid Arabic search
-- (pgvector for semantic search, pg_trgm for fuzzy matching, unaccent as a
-- text-search helper). The `arabic` text-search configuration is built into
-- Postgres core and was already confirmed present.
create extension if not exists vector;
create extension if not exists pg_trgm;
create extension if not exists unaccent;
