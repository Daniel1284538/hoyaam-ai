// litigation-research
//
// POST { query, as_of?, authority_types?, verified_only? }
//   -> { answer, matches, as_of, retrieval, warnings, note }
//
// Plan section 01, Tier B, "Grounded legal research": answers only from
// statutory text and Court of Cassation principles actually retrieved
// from the corpus. If retrieval finds nothing, this returns that fact
// directly and NEVER calls the model — there is nothing to ground an
// answer in, and asking the model anyway would just invite it to answer
// from its own memory, which is exactly the fabrication risk the whole
// design exists to prevent (plan section 03).
//
// Retrieval now goes through fn_search_authorities instead of a bare
// textSearch. Three things changed and each one is load-bearing:
//
//   1. REPEALED LAW IS EXCLUDED. Previously nothing in the schema recorded
//      that an authority had been repealed, so a superseded article read
//      exactly like current law. For a litigation tool that is the single
//      worst failure available. The RPC filters on repealed_date/
//      effective_date against as_of.
//
//   2. RETRIEVAL IS ACTUALLY HYBRID. The vector column and HNSW index
//      existed but nothing read them — this was keyword-only search
//      wearing a semantic-search schema. The query is now embedded and
//      fused with FTS by RRF inside the RPC. If GEMINI_API_KEY is absent
//      or embedding fails, it degrades to FTS-only rather than erroring.
//
//   3. UNVERIFIED TEXT IS FLAGGED, NOT HIDDEN. Bulk-ingested corpus text
//      nobody has checked against the primary source is still usable —
//      refusing it outright would make the tool useless during corpus
//      buildout — but both the model and the caller are told which
//      passages are unverified. verified_only=true excludes them entirely.
//
// as_of exists because litigators routinely need the law as it stood when
// the facts occurred, not as it stands today.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';
const EMBED_MODEL = Deno.env.get('GEMINI_EMBED_MODEL') || 'gemini-embedding-001';
const EMBED_DIMS = 1536;

const AUTHORITY_TYPES = ['statute', 'cassation_principle', 'regulation', 'fiqh_doctrine'];

const SYSTEM_INSTRUCTION =
  'You answer Egyptian legal questions using ONLY the passages provided below — each one is a retrieved chunk from a specific authority with its citation. Every sentence in your answer must be directly supported by one of these passages; reference which passage(s) support each claim. If the passages do not fully answer the question, say so explicitly rather than filling the gap from anything else you know. Never state a statute, article number, or case citation that is not literally present in the passages given. ' +
  'Passages marked [UNVERIFIED] have not been checked against the official source by a qualified person — if you rely on one, say so in your answer. ' +
  'Passages marked [FIQH: <school>] state the doctrine of one school of jurisprudence, not enacted law; never present them as binding statute, and always name the school.';

type Match = {
  chunk_id: string;
  authority_id: string;
  chunk_text: string;
  chunk_ref: string | null;
  title: string;
  citation: string;
  authority_type: string;
  madhhab: string | null;
  verification_status: string;
  effective_date: string | null;
  score: number;
};

// RETRIEVAL_QUERY (not RETRIEVAL_DOCUMENT, which admin-embed-authorities
// uses for the stored passages) — the asymmetric pair the model expects.
async function embedQuery(text: string): Promise<number[] | null> {
  if (!GEMINI_API_KEY) return null;
  try {
    const resp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${EMBED_MODEL}:embedContent?key=${GEMINI_API_KEY}`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          model: `models/${EMBED_MODEL}`,
          content: { parts: [{ text }] },
          taskType: 'RETRIEVAL_QUERY',
          outputDimensionality: EMBED_DIMS,
        }),
      },
    );
    if (!resp.ok) return null;
    const payload = await resp.json();
    const values = payload?.embedding?.values;
    if (!Array.isArray(values) || values.length !== EMBED_DIMS) return null;
    let sum = 0;
    for (const x of values) sum += x * x;
    const norm = Math.sqrt(sum);
    return norm > 0 ? values.map((x: number) => x / norm) : values;
  } catch {
    // Semantic recall is a bonus, not a precondition — fall back to FTS.
    return null;
  }
}

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'run_research', 'grounded_research');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { query, as_of, authority_types, verified_only } = body ?? {};
  if (!query || !String(query).trim()) return badRequest('query is required');

  if (authority_types != null) {
    if (!Array.isArray(authority_types) || authority_types.some((t) => !AUTHORITY_TYPES.includes(t))) {
      return badRequest(`authority_types must be an array of ${AUTHORITY_TYPES.join(', ')}`);
    }
  }
  if (as_of != null && Number.isNaN(Date.parse(as_of))) {
    return badRequest('as_of must be a valid date');
  }

  const asOf = as_of ? String(as_of).slice(0, 10) : new Date().toISOString().slice(0, 10);
  const queryEmbedding = await embedQuery(String(query));

  const { data, error } = await asUser.rpc('fn_search_authorities', {
    p_query_text: String(query),
    p_query_embedding: queryEmbedding ? JSON.stringify(queryEmbedding) : null,
    p_match_count: 10,
    p_as_of: asOf,
    p_authority_types: authority_types ?? null,
    p_include_unverified: verified_only === true ? false : true,
  });
  if (error) return json({ error: error.message }, 500);

  const matches = (data ?? []) as Match[];
  const retrieval = queryEmbedding ? 'hybrid (dense + arabic fts)' : 'arabic fts only (no query embedding available)';

  if (matches.length === 0) {
    return json({
      answer: null,
      matches: [],
      as_of: asOf,
      retrieval,
      note: 'لا توجد نتائج مطابقة في المصادر القانونية المحملة في هذا النظام. لم يطلب من النموذج الإجابة من ذاكرته — هذا خطأ متعمد.',
    });
  }

  const unverified = matches.filter((m) => m.verification_status !== 'human_verified');
  const disputed = matches.filter((m) => m.verification_status === 'disputed');
  const fiqh = matches.filter((m) => m.authority_type === 'fiqh_doctrine');

  const warnings: string[] = [];
  if (unverified.length > 0) {
    warnings.push(`${unverified.length} of ${matches.length} passages have not been verified against the official source by a qualified person.`);
  }
  if (disputed.length > 0) {
    warnings.push(`${disputed.length} passage(s) are flagged as DISPUTED — someone has recorded that the stored text may be wrong.`);
  }
  if (fiqh.length > 0) {
    warnings.push(`${fiqh.length} passage(s) are fiqh doctrine, not enacted law — school-dependent and not binding as statute.`);
  }

  if (!GEMINI_API_KEY) {
    return json({ answer: null, matches, as_of: asOf, retrieval, warnings, error: 'GEMINI_API_KEY is not configured — matches were found but cannot be synthesized into an answer' }, 502);
  }

  const context = matches.map((m, i) => {
    const flags = [
      m.verification_status !== 'human_verified' ? '[UNVERIFIED]' : '',
      m.verification_status === 'disputed' ? '[DISPUTED]' : '',
      m.authority_type === 'fiqh_doctrine' ? `[FIQH: ${m.madhhab ?? 'school unspecified'}]` : '',
    ].filter(Boolean).join(' ');
    return `[${i + 1}] ${flags} ${m.title} (${m.citation})${m.chunk_ref ? ` — ${m.chunk_ref}` : ''}\n${m.chunk_text}`;
  }).join('\n\n');

  let apiResp: Response;
  try {
    apiResp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${GEMINI_API_KEY}`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: SYSTEM_INSTRUCTION }] },
          contents: [{ role: 'user', parts: [{ text: `السؤال: ${query}\n\nالقانون كما هو سارٍ في: ${asOf}\n\nالمقاطع المسترجعة:\n${context}` }] }],
          generationConfig: { temperature: 0 },
        }),
      },
    );
  } catch (e) {
    return json({ answer: null, matches, as_of: asOf, retrieval, warnings, error: `Gemini API request failed: ${e instanceof Error ? e.message : String(e)}` }, 502);
  }
  if (!apiResp.ok) {
    const errText = await apiResp.text().catch(() => '');
    return json({ answer: null, matches, as_of: asOf, retrieval, warnings, error: `Gemini API returned ${apiResp.status}: ${errText.slice(0, 500)}` }, 502);
  }

  const apiJson = await apiResp.json();
  const answer = apiJson.candidates?.[0]?.content?.parts?.find((p: { text?: string }) => typeof p.text === 'string')?.text ?? null;

  await asService.from('audit_log').insert({
    actor_id: callerId, action: 'grounded_research', success: true,
    after: { query, as_of: asOf, match_count: matches.length, retrieval, unverified_count: unverified.length },
  });

  return json({
    answer,
    as_of: asOf,
    retrieval,
    warnings,
    matches: matches.map((m) => ({
      id: m.chunk_id,
      title: m.title,
      citation: m.citation,
      chunk_ref: m.chunk_ref,
      authority_type: m.authority_type,
      madhhab: m.madhhab,
      verification_status: m.verification_status,
      score: m.score,
    })),
  });
});
