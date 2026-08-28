-- Litigation Agent Phase 1 — the legal corpus (authorities, the only
-- citable source for grounded research) and the firm's own drafting
-- templates. Kept structurally separate from documents/matters: different
-- trust level, different retention story, different citation handling.

create table public.authorities (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  authority_type text not null check (authority_type = any (array['statute','cassation_principle','regulation'])),
  citation text not null check (btrim(citation) <> ''),
  source_url text,
  effective_date date,
  added_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);
comment on table public.authorities is 'Statutes and Cassation principles. citation is mandatory by constraint — same pattern as deadline_rules — because this table is the ground truth the citation-fabrication defence rests on (plan section 03).';

create table public.authority_chunks (
  id uuid primary key default gen_random_uuid(),
  authority_id uuid not null references public.authorities(id) on delete cascade,
  chunk_text text not null,
  chunk_ref text,
  embedding vector(1536),
  created_at timestamptz not null default now()
);
comment on table public.authority_chunks is 'Embedded corpus passages — the only thing a citation in a draft is ever allowed to point at. chunk_ref is the pinpoint (article/paragraph) shown alongside the citation.';
create index authority_chunks_authority_id_idx on public.authority_chunks(authority_id);
create index authority_chunks_embedding_hnsw_idx on public.authority_chunks using hnsw (embedding vector_cosine_ops);
create index authority_chunks_fts_idx on public.authority_chunks using gin (to_tsvector('arabic', chunk_text));

create table public.templates (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  doc_type text not null,
  bucket_id text not null default 'templates',
  storage_path text,
  content_text text,
  active boolean not null default true,
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);
comment on table public.templates is 'The firm''s own precedent documents. content_text is what drafting is grounded against — not a generic idea of how a memo should read.';
create index templates_doc_type_idx on public.templates(doc_type) where active;

insert into storage.buckets (id, name, public)
values ('templates', 'templates', false)
on conflict (id) do nothing;
