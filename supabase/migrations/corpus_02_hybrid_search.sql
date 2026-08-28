-- Hybrid retrieval over the legal corpus.
--
-- Until now litigation-research used Arabic full-text search ONLY — the
-- vector column and its HNSW index existed but nothing read them, so
-- "hybrid search" was half-built: exact-term recall with no semantic
-- recall. This function is the missing half, fused properly.
--
-- Fusion is Reciprocal Rank Fusion rather than a weighted sum of raw
-- scores: cosine distance and ts_rank_cd are not on comparable scales, so
-- summing them directly would let whichever scale happens to be larger
-- dominate. RRF only uses each result's *rank* within its own list, which
-- sidesteps normalization entirely.
--
-- SECURITY INVOKER (the default, stated here by omission of DEFINER): the
-- caller's RLS policies on authorities/authority_chunks still apply. This
-- function widens what you can *find*, never what you're allowed to read.
--
-- Dense retrieval degrades gracefully: with p_query_embedding => null (or
-- before any embeddings are backfilled) the dense CTE is empty and this
-- returns pure FTS results, exactly matching the old behaviour.

create or replace function public.fn_search_authorities(
  p_query_text text,
  p_query_embedding vector(1536) default null,
  p_match_count int default 10,
  p_as_of date default current_date,
  p_authority_types text[] default null,
  p_include_unverified boolean default true
)
returns table (
  chunk_id uuid,
  authority_id uuid,
  chunk_text text,
  chunk_ref text,
  title text,
  citation text,
  authority_type text,
  madhhab text,
  verification_status text,
  effective_date date,
  score double precision
)
language sql
stable
as $$
  with in_force as (
    select a.id, a.title, a.citation, a.authority_type, a.madhhab,
           a.verification_status, a.effective_date
    from public.authorities a
    -- The whole point of corpus_01: repealed law must not surface as current.
    where (a.repealed_date is null or a.repealed_date > p_as_of)
      and (a.effective_date is null or a.effective_date <= p_as_of)
      and (p_authority_types is null or a.authority_type = any (p_authority_types))
      and (p_include_unverified or a.verification_status = 'human_verified')
  ),
  dense as (
    select c.id as chunk_id,
           row_number() over (order by c.embedding <=> p_query_embedding) as rank
    from public.authority_chunks c
    join in_force f on f.id = c.authority_id
    where p_query_embedding is not null
      and c.embedding is not null
    order by c.embedding <=> p_query_embedding
    limit greatest(p_match_count * 5, 50)
  ),
  sparse as (
    select c.id as chunk_id,
           row_number() over (
             order by ts_rank_cd(
               to_tsvector('arabic', c.chunk_text),
               websearch_to_tsquery('arabic', p_query_text)
             ) desc
           ) as rank
    from public.authority_chunks c
    join in_force f on f.id = c.authority_id
    where to_tsvector('arabic', c.chunk_text)
          @@ websearch_to_tsquery('arabic', p_query_text)
    order by ts_rank_cd(
      to_tsvector('arabic', c.chunk_text),
      websearch_to_tsquery('arabic', p_query_text)
    ) desc
    limit greatest(p_match_count * 5, 50)
  ),
  fused as (
    -- k = 60 is the standard RRF constant; it damps the influence of the
    -- very top ranks just enough that one list can't unilaterally decide
    -- the fused order.
    select coalesce(d.chunk_id, s.chunk_id) as chunk_id,
           coalesce(1.0 / (60 + d.rank), 0) + coalesce(1.0 / (60 + s.rank), 0) as score
    from dense d
    full outer join sparse s on s.chunk_id = d.chunk_id
  )
  select c.id, f.id, c.chunk_text, c.chunk_ref, f.title, f.citation,
         f.authority_type, f.madhhab, f.verification_status, f.effective_date,
         fused.score
  from fused
  join public.authority_chunks c on c.id = fused.chunk_id
  join in_force f on f.id = c.authority_id
  order by fused.score desc
  limit p_match_count;
$$;

revoke execute on function public.fn_search_authorities(text, vector, int, date, text[], boolean) from public;
grant execute on function public.fn_search_authorities(text, vector, int, date, text[], boolean) to authenticated;

comment on function public.fn_search_authorities(text, vector, int, date, text[], boolean) is
  'Hybrid (dense + Arabic FTS) retrieval over authority_chunks, fused with RRF. Excludes authorities repealed or not yet in force as of p_as_of. SECURITY INVOKER: caller RLS still applies. Falls back to FTS-only when no query embedding is supplied.';
