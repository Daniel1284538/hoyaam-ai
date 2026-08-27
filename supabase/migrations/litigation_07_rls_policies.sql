-- Litigation Agent Phase 1 — RLS. Every table follows the existing
-- convention: a RESTRICTIVE mfa_verified() policy ANDed with a PERMISSIVE
-- access-based SELECT policy. No INSERT/UPDATE/DELETE policies anywhere
-- in this schema — every write goes through a service-role Edge Function
-- with its own requireCapability() check, matching Phase A-E.

alter table public.matter_parties enable row level security;
create policy matter_parties_require_mfa on public.matter_parties as restrictive for select using (mfa_verified());
create policy matter_parties_select on public.matter_parties for select using (can_access_matter(matter_id));

alter table public.hearings enable row level security;
create policy hearings_require_mfa on public.hearings as restrictive for select using (mfa_verified());
create policy hearings_select on public.hearings for select using (can_access_matter(matter_id));

alter table public.deadlines enable row level security;
create policy deadlines_require_mfa on public.deadlines as restrictive for select using (mfa_verified());
create policy deadlines_select on public.deadlines for select using (can_access_matter(matter_id));

alter table public.documents enable row level security;
create policy documents_require_mfa on public.documents as restrictive for select using (mfa_verified());
create policy documents_select on public.documents for select using (can_access_matter(matter_id));

alter table public.document_pages enable row level security;
create policy document_pages_require_mfa on public.document_pages as restrictive for select using (mfa_verified());
create policy document_pages_select on public.document_pages for select using (
  exists (select 1 from public.documents d where d.id = document_pages.document_id and can_access_matter(d.matter_id))
);

alter table public.document_chunks enable row level security;
create policy document_chunks_require_mfa on public.document_chunks as restrictive for select using (mfa_verified());
create policy document_chunks_select on public.document_chunks for select using (
  exists (select 1 from public.documents d where d.id = document_chunks.document_id and can_access_matter(d.matter_id))
);

alter table public.extractions enable row level security;
create policy extractions_require_mfa on public.extractions as restrictive for select using (mfa_verified());
create policy extractions_select on public.extractions for select using (
  can_access_matter(matter_id) or has_capability('review_extractions')
);

-- Corpus tables are firm-wide, not matter-scoped — gated by capability
-- alone, same shape as deadline_rules_select.
alter table public.authorities enable row level security;
create policy authorities_require_mfa on public.authorities as restrictive for select using (mfa_verified());
create policy authorities_select on public.authorities for select using (
  has_capability('run_research') or has_capability('generate_draft') or has_capability('manage_corpus')
);

alter table public.authority_chunks enable row level security;
create policy authority_chunks_require_mfa on public.authority_chunks as restrictive for select using (mfa_verified());
create policy authority_chunks_select on public.authority_chunks for select using (
  has_capability('run_research') or has_capability('generate_draft') or has_capability('manage_corpus')
);

alter table public.templates enable row level security;
create policy templates_require_mfa on public.templates as restrictive for select using (mfa_verified());
create policy templates_select on public.templates for select using (
  has_capability('manage_templates') or has_capability('generate_draft')
);

alter table public.drafts enable row level security;
create policy drafts_require_mfa on public.drafts as restrictive for select using (mfa_verified());
create policy drafts_select on public.drafts for select using (can_access_matter(matter_id));

alter table public.draft_citations enable row level security;
create policy draft_citations_require_mfa on public.draft_citations as restrictive for select using (mfa_verified());
create policy draft_citations_select on public.draft_citations for select using (
  exists (select 1 from public.drafts dr where dr.id = draft_citations.draft_id and can_access_matter(dr.matter_id))
);

alter table public.agent_runs enable row level security;
create policy agent_runs_require_mfa on public.agent_runs as restrictive for select using (mfa_verified());
create policy agent_runs_select on public.agent_runs for select using (
  has_capability('view_costs') or (matter_id is not null and can_access_matter(matter_id))
);

alter table public.review_actions enable row level security;
create policy review_actions_require_mfa on public.review_actions as restrictive for select using (mfa_verified());
create policy review_actions_select on public.review_actions for select using (
  has_capability('view_audit_log') or (matter_id is not null and can_access_matter(matter_id))
);
