-- Advisor flagged vector/pg_trgm/unaccent as installed in public. Move them
-- to the extensions schema, matching where pgcrypto/uuid-ossp already live
-- in this project — the default search_path already resolves through it,
-- as proven by every gen_random_uuid() call across every table so far.
alter extension vector set schema extensions;
alter extension pg_trgm set schema extensions;
alter extension unaccent set schema extensions;
