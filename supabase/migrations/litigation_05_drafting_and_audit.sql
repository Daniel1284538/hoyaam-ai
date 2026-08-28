-- Litigation Agent Phase 1 — drafts, the citation-binding table that makes
-- fabrication structurally blockable, and the audit trail.

create table public.drafts (
  id uuid primary key default gen_random_uuid(),
  matter_id uuid not null references public.matters(id) on delete cascade,
  doc_type text not null,
  template_id uuid references public.templates(id),
  version integer not null default 1,
  bucket_id text not null default 'matter-drafts',
  storage_path text,
  content_text text,
  status text not null default 'drafting' check (status = any (array['drafting','pending_citation_check','ready_for_review','approved','exported'])),
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);
comment on table public.drafts is 'Generated documents, versioned per edit. status=exported means a human confirmed every citation and Word export ran — see draft_citations.';
create index drafts_matter_id_idx on public.drafts(matter_id, version desc);

create table public.draft_citations (
  id uuid primary key default gen_random_uuid(),
  draft_id uuid not null references public.drafts(id) on delete cascade,
  citation_text text not null,
  authority_chunk_id uuid references public.authority_chunks(id),
  status text not null default 'unverified' check (status = any (array['unverified','verified','flagged'])),
  created_at timestamptz not null default now()
);
comment on table public.draft_citations is
  'Every legal reference in a draft, one row each. authority_chunk_id is null until the API''s citations feature binds it to an actual retrieved passage. status=flagged (غير موثق) blocks export — this table is the mechanism behind the "structurally impossible" fabrication defence in plan section 03, not the rules table.';
create index draft_citations_draft_id_idx on public.draft_citations(draft_id);
create index draft_citations_flagged_idx on public.draft_citations(status) where status = 'flagged';

-- Full technical audit, distinct from usage_ledger (which exists already
-- and drives the Cost page — dollars in, dollars out). This is the
-- per-run record: what was asked, what tools were called, what came back.
create table public.agent_runs (
  id uuid primary key default gen_random_uuid(),
  matter_id uuid references public.matters(id),
  run_type text not null check (run_type = any (array['extraction','drafting','research','chat'])),
  model text not null,
  input_summary text,
  tool_calls jsonb,
  input_tokens integer,
  output_tokens integer,
  cost_usd numeric,
  status text not null default 'completed' check (status = any (array['completed','failed'])),
  error text,
  triggered_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);
comment on table public.agent_runs is 'Prompt, model, tokens, cost, tool calls — full run audit (plan data model, section 05). matter_id nullable: a research query can precede a matter existing.';
create index agent_runs_matter_id_idx on public.agent_runs(matter_id);

create table public.review_actions (
  id uuid primary key default gen_random_uuid(),
  subject_type text not null check (subject_type = any (array['extraction','draft_citation','draft','deadline'])),
  subject_id uuid not null,
  matter_id uuid references public.matters(id),
  action text not null,
  actor_id uuid references auth.users(id),
  notes text,
  created_at timestamptz not null default now()
);
comment on table public.review_actions is 'Who approved what, when — accountability record (plan data model, section 05). subject_type + subject_id points at the row being reviewed rather than one FK per reviewable table.';
create index review_actions_subject_idx on public.review_actions(subject_type, subject_id);
create index review_actions_matter_id_idx on public.review_actions(matter_id);
