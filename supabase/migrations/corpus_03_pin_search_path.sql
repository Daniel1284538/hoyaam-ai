-- Pin the search_path on the retrieval function (advisor 0011).
--
-- Must include `extensions`, not just `public`: pgvector is installed in
-- the extensions schema on this project, so a bare `search_path = public`
-- would leave the `<=>` cosine operator unresolvable at runtime and break
-- dense retrieval entirely.
alter function public.fn_search_authorities(text, vector, int, date, text[], boolean)
  set search_path = public, extensions;
