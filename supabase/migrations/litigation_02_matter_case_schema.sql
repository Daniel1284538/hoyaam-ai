-- Litigation Agent Phase 1 — real case schema on the matters stub, plus
-- the tables the plan calls matter_parties / hearings / deadlines.
-- Per the stub's own comment: ALTER matters here, never recreate it.

alter table public.matters
  add column court text,
  add column circuit text,
  add column case_number text,
  add column case_year integer,
  add column matter_type text,
  add column stage text,
  add column subject text,
  add column opened_at date not null default current_date;

alter table public.matters
  add constraint matters_stage_check
    check (stage is null or stage = any (array['first_instance','appeal','cassation','execution']));

-- A given court file (court + case_number + case_year) should only exist
-- once. Partial index so pre-case-number stubs (intake in progress) don't
-- collide with each other.
create unique index matters_case_identity_uidx
  on public.matters (court, case_number, case_year)
  where case_number is not null and court is not null;

comment on column public.matters.matter_type is
  'Free text, matching retention_policies.matter_type — no enum, since the firm''s own practice-area vocabulary should drive this, not an engineer''s guess.';

create table public.matter_parties (
  id uuid primary key default gen_random_uuid(),
  matter_id uuid not null references public.matters(id) on delete cascade,
  party_role text not null check (party_role = any (array['plaintiff','defendant','third_party','counsel_own_side','counsel_opposing'])),
  name text not null,
  identifier text,
  notes text,
  created_at timestamptz not null default now()
);
comment on table public.matter_parties is 'Plaintiffs, defendants, counsel, role per party — plan section 05.';
create index matter_parties_matter_id_idx on public.matter_parties(matter_id);

create table public.hearings (
  id uuid primary key default gen_random_uuid(),
  matter_id uuid not null references public.matters(id) on delete cascade,
  session_date date not null,
  session_time time,
  outcome text check (outcome is null or outcome = any (array['adjourned','reserved_for_judgment','judgment_issued','other'])),
  adjournment_reason text,
  next_session_date date,
  recorded_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);
comment on table public.hearings is 'The hearing roll. next_session_date drives the "one rolling roll across every matter" view — plan section 01.';
create index hearings_matter_id_idx on public.hearings(matter_id, session_date desc);

-- The per-matter computed-deadline instance table. deadline_rules (already
-- in the schema) is the lawyer-authored rulebook; this is where a specific
-- rule gets applied to a specific matter's triggering event. Always starts
-- 'provisional' — see fn_compute_deadline in the next migration and the
-- "Deadlines" hard problem in the plan (section 03).
create table public.deadlines (
  id uuid primary key default gen_random_uuid(),
  matter_id uuid not null references public.matters(id) on delete cascade,
  rule_id uuid not null references public.deadline_rules(id),
  trigger_event text not null,
  trigger_date date not null,
  computed_due_date date not null,
  status text not null default 'provisional' check (status = any (array['provisional','confirmed','passed','not_applicable'])),
  confirmed_by uuid references auth.users(id),
  confirmed_at timestamptz,
  created_at timestamptz not null default now(),
  constraint deadlines_confirmed_together check ((confirmed_by is null) = (confirmed_at is null))
);
comment on table public.deadlines is 'Computed dates, provisional until a lawyer confirms them — never auto-trusted. See fn_compute_deadline.';
create index deadlines_matter_id_idx on public.deadlines(matter_id, computed_due_date);
create index deadlines_status_idx on public.deadlines(status) where status = 'provisional';
