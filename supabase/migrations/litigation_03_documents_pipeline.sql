-- Litigation Agent Phase 1 — the ingest/extract pipeline: documents,
-- their pages (for page-accurate citing), their embedded chunks (for
-- hybrid search), and the extracted fields (confidence-gated, reviewed
-- by a human before anything trusts them).
--
-- Embedding dimension: 1536, matching a standard third-party embeddings
-- provider (e.g. text-embedding-3-small) per the plan's "Embeddings:
-- separate provider" cost line. Change this migration before ingesting
-- real data if a different model/dimension is chosen.

create table public.documents (
  id uuid primary key default gen_random_uuid(),
  matter_id uuid not null references public.matters(id) on delete cascade,
  bucket_id text not null default 'matter-documents',
  storage_path text not null,
  original_filename text,
  mime_type text,
  page_count integer,
  ocr_status text not null default 'pending' check (ocr_status = any (array['pending','processing','done','failed'])),
  uploaded_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);
comment on table public.documents is 'One row per uploaded file. ocr_status tracks legal-extract''s progress; job_events on the linked ingestion_jobs row carries the detail.';
create index documents_matter_id_idx on public.documents(matter_id);

create table public.document_pages (
  id uuid primary key default gen_random_uuid(),
  document_id uuid not null references public.documents(id) on delete cascade,
  page_number integer not null,
  text_content text,
  layout jsonb,
  created_at timestamptz not null default now(),
  unique (document_id, page_number)
);
comment on table public.document_pages is 'Per-page OCR text and layout. Every chunk and every extraction traces back to a row here for page-accurate citing (plan section 03).';

create table public.document_chunks (
  id uuid primary key default gen_random_uuid(),
  document_id uuid not null references public.documents(id) on delete cascade,
  page_id uuid references public.document_pages(id) on delete cascade,
  chunk_text text not null,
  embedding vector(1536),
  created_at timestamptz not null default now()
);
comment on table public.document_chunks is 'Embedded passages for archive search, with a page back-reference. Retrieval is hybrid: this table''s embedding column fused with Arabic full-text search over chunk_text.';
create index document_chunks_document_id_idx on public.document_chunks(document_id);
create index document_chunks_embedding_hnsw_idx on public.document_chunks using hnsw (embedding vector_cosine_ops);
create index document_chunks_fts_idx on public.document_chunks using gin (to_tsvector('arabic', chunk_text));

create table public.extractions (
  id uuid primary key default gen_random_uuid(),
  document_id uuid not null references public.documents(id) on delete cascade,
  matter_id uuid not null references public.matters(id) on delete cascade,
  field_key text not null,
  field_value text,
  confidence numeric check (confidence is null or (confidence >= 0 and confidence <= 1)),
  review_status text not null default 'pending' check (review_status = any (array['pending','confirmed','corrected','rejected'])),
  reviewed_by uuid references auth.users(id),
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  constraint extractions_reviewed_together check ((reviewed_by is null) = (reviewed_at is null))
);
comment on table public.extractions is 'One row per extracted field. Below-threshold confidence routes here for human review instead of writing straight into matters/matter_parties/hearings — see review_extractions capability.';
create index extractions_matter_id_idx on public.extractions(matter_id);
create index extractions_review_queue_idx on public.extractions(review_status) where review_status = 'pending';
