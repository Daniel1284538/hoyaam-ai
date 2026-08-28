-- Corpus phase 1: make an authority's *legal currency* and *provenance* first-class.
--
-- The single most dangerous failure mode for a legal RAG system is citing a
-- repealed article as current law. Until now `authorities` had no way to say
-- "this is no longer in force" — so every row retrieved read as current.
-- Same for trust: a machine-scraped article and a lawyer-verified one were
-- indistinguishable. Both are now explicit columns, and retrieval filters on
-- them (see corpus_02).

-- Fiqh doctrine is a genuinely different kind of authority from a statute:
-- it is school-dependent and interpretive, not enacted text. It gets its own
-- type so it can never be silently mixed into statutory retrieval.
alter table public.authorities drop constraint authorities_authority_type_check;
alter table public.authorities add constraint authorities_authority_type_check
  check (authority_type in ('statute', 'cassation_principle', 'regulation', 'fiqh_doctrine'));

alter table public.authorities
  -- null = in force. Set = repealed/superseded as of this date.
  add column repealed_date date,
  add column superseded_by uuid references public.authorities(id),
  add column amendment_note text,

  -- Trust ladder. Nothing bulk-ingested is trusted until a qualified human
  -- signs it off against the primary source; 'disputed' exists because
  -- "we checked and it's wrong" is different from "we never checked".
  add column verification_status text not null default 'machine_ingested'
    check (verification_status in ('machine_ingested', 'human_verified', 'disputed')),
  add column verified_by uuid references auth.users(id),
  add column verified_at timestamptz,

  -- Only meaningful for fiqh_doctrine. Egyptian Personal Status law falls
  -- back to the preponderant Hanafi opinion where the codes are silent, so
  -- which school a passage states is legally load-bearing, not metadata.
  add column madhhab text
    check (madhhab in ('hanafi', 'maliki', 'shafii', 'hanbali')),

  add column language text not null default 'ar',

  -- Lets an amendment-watch job detect that the upstream text changed
  -- under us without re-reading every article by hand.
  add column source_hash text,
  add column source_fetched_at timestamptz;

-- Paired constraints, same style as data_requests' scoped/decided pairs:
-- a state and its evidence are set together or not at all.
alter table public.authorities
  add constraint authorities_verified_together check (
    (verification_status = 'machine_ingested' and verified_by is null and verified_at is null)
    or (verification_status <> 'machine_ingested' and verified_by is not null and verified_at is not null)
  ),
  -- Something can only be *superseded by* another authority if it is in fact repealed.
  add constraint authorities_superseded_implies_repealed check (
    superseded_by is null or repealed_date is not null
  ),
  -- A madhhab on a statute would be a category error.
  add constraint authorities_madhhab_only_on_fiqh check (
    madhhab is null or authority_type = 'fiqh_doctrine'
  ),
  -- An authority cannot supersede itself.
  add constraint authorities_no_self_supersede check (
    superseded_by is null or superseded_by <> id
  );

-- Which model produced each embedding, so switching embedding models is a
-- detectable, re-runnable migration rather than a silent correctness bug
-- (vectors from different models are not comparable).
alter table public.authority_chunks
  add column embedding_model text,
  add column embedded_at timestamptz,
  add constraint authority_chunks_embedding_together check (
    (embedding is null and embedding_model is null and embedded_at is null)
    or (embedding is not null and embedding_model is not null and embedded_at is not null)
  );

create index authorities_in_force_idx on public.authorities (repealed_date)
  where repealed_date is null;
create index authorities_verification_status_idx on public.authorities (verification_status);
create index authority_chunks_unembedded_idx on public.authority_chunks (id)
  where embedding is null;

-- Defense in depth against self-verification, mirroring the existing
-- self-grant / self-approval trigger pairs. The Edge Function checks this
-- too; this is the half that still holds if someone reaches the table by
-- another path.
create or replace function public.fn_block_self_verification()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.verification_status <> 'machine_ingested'
     and new.verified_by is not null
     and new.added_by is not null
     and new.verified_by = new.added_by then
    raise exception 'authorities: % cannot verify an authority they added themselves', new.verified_by
      using errcode = 'P0001';
  end if;
  return new;
end;
$$;

revoke execute on function public.fn_block_self_verification() from public, anon, authenticated;

create trigger trg_authorities_no_self_verification
  before insert or update on public.authorities
  for each row execute function public.fn_block_self_verification();

create trigger trg_audit_authorities
  after insert or update or delete on public.authorities
  for each row execute function public.fn_audit_row_change();

comment on column public.authorities.repealed_date is
  'null = in force. Retrieval filters on this — a repealed article must never surface as current law.';
comment on column public.authorities.verification_status is
  'machine_ingested until a qualified human checks the text against the primary source. Surfaced to the user at answer time.';
comment on column public.authorities.madhhab is
  'Only on fiqh_doctrine. Egyptian Personal Status law defaults to the preponderant Hanafi opinion where the codes are silent.';
