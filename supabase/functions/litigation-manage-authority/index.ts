// litigation-manage-authority
//
// POST { title, authority_type, citation, source_url?, effective_date?,
//        repealed_date?, superseded_by?, amendment_note?, madhhab?,
//        language?, source_hash?, source_fetched_at?,
//        chunks: [{ chunk_text, chunk_ref? }] }
//
// Manual entry into the legal corpus (plan section 04, Phase 4: "source,
// ingest, and structure statutes and Cassation principles"). No bulk
// import / OCR-of-legislation pipeline exists here — the plan itself
// flags sourcing this corpus as the genuine long pole, needing a licensed
// or hand-assembled source, not something an engineer fills in. This
// function exists so a real corpus CAN be loaded once one is sourced; it
// does not itself contain or generate any legal text.
//
// citation is mandatory by the same DB constraint as deadline_rules —
// every authority must be attributable from the moment it's added.
//
// Everything inserted here starts at verification_status='machine_ingested'.
// It is deliberately NOT settable from this endpoint: promotion to
// 'human_verified' goes through admin-verify-authority, which enforces
// that the verifier is not the person who added it. Letting an ingest
// call assert its own text was verified would defeat the entire point of
// having a trust ladder.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const AUTHORITY_TYPES = ['statute', 'cassation_principle', 'regulation', 'fiqh_doctrine'];
const MADHHABS = ['hanafi', 'maliki', 'shafii', 'hanbali'];

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'manage_corpus', 'authority_added');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const {
    title, authority_type, citation, source_url, effective_date,
    repealed_date, superseded_by, amendment_note, madhhab, language,
    source_hash, source_fetched_at, chunks,
  } = body ?? {};

  if (!title || !AUTHORITY_TYPES.includes(authority_type) || !citation || !String(citation).trim()) {
    return badRequest(`title, a valid authority_type (${AUTHORITY_TYPES.join(', ')}), and a non-empty citation are required`);
  }
  if (!Array.isArray(chunks) || chunks.length === 0 || chunks.some((c) => !c?.chunk_text || !String(c.chunk_text).trim())) {
    return badRequest('chunks must be a non-empty array of { chunk_text, chunk_ref? }');
  }

  // Mirrors authorities_madhhab_only_on_fiqh. Checked here too so the
  // caller gets a useful message instead of a raw constraint violation.
  if (madhhab != null) {
    if (!MADHHABS.includes(madhhab)) {
      return badRequest(`madhhab must be one of ${MADHHABS.join(', ')}`);
    }
    if (authority_type !== 'fiqh_doctrine') {
      return badRequest('madhhab may only be set on authority_type=fiqh_doctrine — a school of jurisprudence is not a property of enacted legislation');
    }
  }

  // Mirrors authorities_superseded_implies_repealed.
  if (superseded_by && !repealed_date) {
    return badRequest('superseded_by requires repealed_date — an authority can only be superseded if it is in fact repealed');
  }

  const { data: authority, error } = await asService
    .from('authorities')
    .insert({
      title,
      authority_type,
      citation,
      source_url: source_url || null,
      effective_date: effective_date || null,
      repealed_date: repealed_date || null,
      superseded_by: superseded_by || null,
      amendment_note: amendment_note || null,
      madhhab: madhhab || null,
      language: language || 'ar',
      source_hash: source_hash || null,
      source_fetched_at: source_fetched_at || null,
      added_by: callerId,
    })
    .select('id')
    .single();
  if (error) return json({ error: error.message }, 409);

  const rows = chunks.map((c: { chunk_text: string; chunk_ref?: string }) => ({
    authority_id: authority.id, chunk_text: c.chunk_text, chunk_ref: c.chunk_ref || null,
  }));
  const { error: chunkError } = await asService.from('authority_chunks').insert(rows);
  if (chunkError) return json({ authority_id: authority.id, error: `authority created but chunks failed: ${chunkError.message}` }, 207);

  await logAction(asService, callerId, 'authority_added', {
    targetTable: 'authorities', targetId: authority.id,
    after: { title, authority_type, citation, madhhab: madhhab || null, repealed_date: repealed_date || null, chunk_count: rows.length },
  });

  return json({
    authority_id: authority.id,
    chunk_count: rows.length,
    verification_status: 'machine_ingested',
    note: 'Chunks are stored but NOT yet embedded — run admin-embed-authorities to make them semantically retrievable. Until a different user verifies this authority it stays machine_ingested and is flagged as unverified at answer time.',
  });
});
