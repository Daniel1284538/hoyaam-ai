// litigation-search-archive
//
// POST { query, matter_id?, court? }
//
// Plan section 01, "Archive search": ask the firm's own past filings in
// plain Arabic and get the memo, the page, and the passage. This is the
// FULL-TEXT half of the hybrid design (plan section 03) — Arabic
// websearch_to_tsquery over document_chunks.chunk_text. The SEMANTIC half
// (pgvector cosine over document_chunks.embedding) is wired in schema
// (hnsw index already exists) but INACTIVE: nothing populates
// document_chunks.embedding yet, because no embeddings-provider key is
// configured in this project. Results below are text-match only until
// that's added and a backfill embeds existing chunks.
//
// Runs as the CALLER's own client (asUser), not service_role — RLS on
// document_chunks (via documents.matter_id -> can_access_matter) does the
// matter-scoping for free; this function only adds the run_research
// feature gate on top of that.
//
// court filters against the real matters.court column (see the
// client-side filter below). There is deliberately no doc_type filter
// (contract/memo/pleading/judgment, as a design reference for this UI
// proposed): documents has no such column, and nothing in this pipeline
// classifies a filing by legal document type — inventing one here would
// be a filter over data that doesn't exist.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'run_research', 'archive_search');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { query, matter_id, court } = body ?? {};
  if (!query || !String(query).trim()) return badRequest('query is required');

  // court is filtered client-side below rather than as a second nested
  // .eq() (documents.matters.court) — PostgREST's support for filtering
  // an embed two joins deep is not something to rely on untested here.
  // Overfetch when a court filter is requested so filtering afterward
  // still has enough to work with.
  let q = asUser
    .from('document_chunks')
    .select('id, document_id, chunk_text, document_pages(page_number), documents!inner(matter_id, original_filename, matters(matter_label, court, circuit, case_number, case_year))')
    .textSearch('chunk_text', query, { type: 'websearch', config: 'arabic' })
    .limit(court ? 100 : 20);
  if (matter_id) q = q.eq('documents.matter_id', matter_id);

  const { data, error } = await q;
  if (error) return json({ error: error.message }, 500);

  let rows = data || [];
  if (court) rows = rows.filter((r: any) => r.documents?.matters?.court === court);
  rows = rows.slice(0, 20);

  return json({
    results: rows.map((r: any) => {
      const doc = r.documents;
      const matter = doc?.matters;
      return {
        chunk_id: r.id,
        document_id: r.document_id,
        document_name: doc?.original_filename ?? null,
        matter_id: doc?.matter_id ?? null,
        matter_label: matter?.matter_label ?? null,
        court: matter?.court ?? null,
        circuit: matter?.circuit ?? null,
        case_number: matter?.case_number ?? null,
        case_year: matter?.case_year ?? null,
        page_number: r.document_pages?.page_number ?? null,
        excerpt: String(r.chunk_text || '').slice(0, 400),
      };
    }),
    semantic_search_active: false,
    note: 'بحث نصي فقط (Arabic full-text) — البحث الدلالي (embeddings) غير مفعّل بعد',
  });
});
