// litigation-memo
//
// POST { matter_id, question, authority_types?, verified_only?, as_of? }
//   -> { created: false, matches: [], note } | { created: true, draft_id, version, matches, citations_bound, warnings }
//
// The Legal Memo & Analysis Builder: a workflow distinct from
// litigation-draft. A draft is "write this pleading from our template and
// this matter's facts" — a memo is "answer this legal question about the
// matter as a structured analysis (Issue / Rule / Application /
// Conclusion), grounded only in retrieved authorities". It shares
// litigation-draft's storage (drafts, doc_type='legal_memo') and export
// path (litigation-export-draft is already generic over doc_type), but
// its retrieval and citation-binding behave like litigation-research, not
// like litigation-draft.
//
// Same fabrication guard as litigation-research, applied one step
// further: if retrieval finds zero matches, this does NOT call the model
// AND does not create a draft at all — there would be nothing for the
// Rule section to be grounded in, so a memo would just be an invented
// answer wearing a structured format. The caller gets the same explicit
// "no results" note back instead.
//
// Unlike litigation-draft (which cites nothing, because nothing existed
// to cite), a memo's citations are real from the moment retrieval
// succeeds: every authority the model says it relied on is a passage it
// was actually given, so draft_citations rows are created immediately,
// pre-bound to the real authority_chunk_id, with status derived from that
// authority's own verification_status — verified stays only for
// human_verified sources, disputed sources come in pre-flagged. Nothing
// here marks a citation "verified" that a human hasn't actually checked;
// see litigation-verify-citation / the Citation Inspector for that step.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';
const EMBED_MODEL = Deno.env.get('GEMINI_EMBED_MODEL') || 'gemini-embedding-001';
const EMBED_DIMS = 1536;
const MAX_CONTEXT_CHARS = 60000;

const AUTHORITY_TYPES = ['statute', 'cassation_principle', 'regulation', 'fiqh_doctrine'];

const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  required: ['issue', 'rule', 'application', 'conclusion', 'citations'],
  properties: {
    issue: { type: 'STRING', description: 'The precise legal question being analyzed, in Arabic.' },
    rule: { type: 'STRING', description: 'The governing legal rule, stated ONLY from the retrieved passages, in Arabic.' },
    application: { type: 'STRING', description: 'How the rule applies to this matter\'s actual facts, in Arabic.' },
    conclusion: { type: 'STRING', description: 'The resulting analysis/recommendation, in Arabic.' },
    citations: {
      type: 'ARRAY',
      description: 'Every retrieved passage the Rule section actually relied on. Do not list a passage you did not use.',
      items: {
        type: 'OBJECT', required: ['passage_index'],
        properties: { passage_index: { type: 'INTEGER', description: 'The [n] number of the passage as given below, 1-indexed.' } },
      },
    },
  },
};

const SYSTEM_INSTRUCTION =
  'You write a structured Egyptian legal memo (IRAC: Issue, Rule, Application, Conclusion) in Arabic for a lawyer to review — this is a first analysis draft, not final advice, and the lawyer signs off before it reaches a client or court. ' +
  'Ground the Rule section ONLY in the retrieved passages given below — each is a specific authority with its citation. Never state a statute, article number, or case citation that is not literally present in the passages given. ' +
  'Ground the Application section ONLY in the matter facts given below — never invent facts, dates, or figures. ' +
  'In "citations", list the passage_index of every passage the Rule section actually relied on — never invent a passage_index outside the given range, and never list one you did not use. ' +
  'Passages marked [UNVERIFIED] have not been checked against the official source by a qualified person — say so in the Rule section if you rely on one. ' +
  'Passages marked [FIQH: <school>] state one school of jurisprudence, not enacted law — never present as binding statute, and always name the school.';

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

  const capError = await requireCapability(asUser, asService, callerId, 'generate_draft', 'memo_generated');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { matter_id, question, authority_types, verified_only, as_of } = body ?? {};
  if (!matter_id || !question || !String(question).trim()) return badRequest('matter_id and question are required');

  if (authority_types != null) {
    if (!Array.isArray(authority_types) || authority_types.some((t) => !AUTHORITY_TYPES.includes(t))) {
      return badRequest(`authority_types must be an array of ${AUTHORITY_TYPES.join(', ')}`);
    }
  }
  if (as_of != null && Number.isNaN(Date.parse(as_of))) return badRequest('as_of must be a valid date');

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  const asOf = as_of ? String(as_of).slice(0, 10) : new Date().toISOString().slice(0, 10);
  const queryEmbedding = await embedQuery(String(question));

  const { data, error } = await asUser.rpc('fn_search_authorities', {
    p_query_text: String(question),
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
      created: false,
      matches: [],
      as_of: asOf,
      retrieval,
      note: 'لا توجد نتائج مطابقة في المصادر القانونية المحملة في هذا النظام. لم يُطلب من النموذج توليد مذكرة بلا استشهادات حقيقية — هذا خطأ متعمد.',
    });
  }

  if (!GEMINI_API_KEY) {
    return json({ created: false, matches, as_of: asOf, retrieval, error: 'GEMINI_API_KEY is not configured — matches were found but a memo cannot be synthesized' }, 502);
  }

  const [{ data: matter }, { data: parties }] = await Promise.all([
    asService.from('matters').select('matter_label, court, circuit, case_number, case_year, matter_type, stage, subject').eq('id', matter_id).single(),
    asService.from('matter_parties').select('party_role, name, identifier').eq('matter_id', matter_id),
  ]);

  let factsContext = `بيانات القضية:\n${JSON.stringify(matter, null, 2)}\n\nالأطراف:\n${JSON.stringify(parties, null, 2)}`;
  if (factsContext.length > MAX_CONTEXT_CHARS) factsContext = factsContext.slice(0, MAX_CONTEXT_CHARS);

  const passagesContext = matches.map((m, i) => {
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
          contents: [{
            role: 'user',
            parts: [{ text: `المسألة القانونية: ${question}\n\nالقانون كما هو سارٍ في: ${asOf}\n\n${factsContext}\n\nالمقاطع المسترجعة:\n${passagesContext}` }],
          }],
          generationConfig: { temperature: 0.2, responseMimeType: 'application/json', responseSchema: RESPONSE_SCHEMA },
        }),
      },
    );
  } catch (e) {
    return json({ created: false, matches, as_of: asOf, retrieval, error: `Gemini API request failed: ${e instanceof Error ? e.message : String(e)}` }, 502);
  }
  if (!apiResp.ok) {
    const errText = await apiResp.text().catch(() => '');
    return json({ created: false, matches, as_of: asOf, retrieval, error: `Gemini API returned ${apiResp.status}: ${errText.slice(0, 500)}` }, 502);
  }

  const apiJson = await apiResp.json();
  const blockReason = apiJson.promptFeedback?.blockReason;
  if (blockReason) return json({ created: false, matches, as_of: asOf, retrieval, error: `Gemini blocked the request: ${blockReason}` }, 502);

  const textPart = apiJson.candidates?.[0]?.content?.parts?.find((p: { text?: string }) => typeof p.text === 'string');
  if (!textPart) return json({ created: false, matches, as_of: asOf, retrieval, error: 'model did not return the expected JSON output' }, 502);

  let memoOut: { issue: string; rule: string; application: string; conclusion: string; citations: { passage_index: number }[] };
  try {
    memoOut = JSON.parse(textPart.text);
  } catch {
    return json({ created: false, matches, as_of: asOf, retrieval, error: 'model output was not valid JSON' }, 502);
  }

  // Never trust the model's own indices beyond bounds-checking them —
  // this is the one place a hallucinated index could silently bind a
  // citation to the wrong passage.
  const seen = new Set<number>();
  const usedIndices = (memoOut.citations || [])
    .map((c) => c?.passage_index)
    .filter((idx): idx is number => Number.isInteger(idx) && idx >= 1 && idx <= matches.length && !seen.has(idx) && seen.add(idx));

  const contentText =
    `${question}\n\n` +
    `المسألة القانونية:\n${memoOut.issue}\n\n` +
    `القاعدة القانونية:\n${memoOut.rule}\n\n` +
    `التطبيق على وقائع القضية:\n${memoOut.application}\n\n` +
    `الخلاصة:\n${memoOut.conclusion}`;

  const { data: existing } = await asService
    .from('drafts')
    .select('version')
    .eq('matter_id', matter_id)
    .eq('doc_type', 'legal_memo')
    .order('version', { ascending: false })
    .limit(1)
    .maybeSingle();
  const version = (existing?.version ?? 0) + 1;

  const { data: draft, error: draftError } = await asService
    .from('drafts')
    .insert({ matter_id, doc_type: 'legal_memo', version, content_text: contentText, status: 'ready_for_review', created_by: callerId })
    .select('id')
    .single();
  if (draftError) return json({ error: draftError.message }, 500);

  const citationRows = usedIndices.map((idx) => {
    const m = matches[idx - 1];
    return {
      draft_id: draft.id,
      citation_text: `${m.title} (${m.citation})${m.chunk_ref ? ` — ${m.chunk_ref}` : ''}`,
      authority_chunk_id: m.chunk_id,
      // Real retrieval, real binding — but "verified" here still means
      // the corpus source itself was human-verified, not that a lawyer
      // has checked this specific memo's use of it. A disputed source
      // comes in pre-flagged so it can't slip through export unnoticed.
      status: m.verification_status === 'disputed' ? 'flagged' : m.verification_status === 'human_verified' ? 'verified' : 'unverified',
    };
  });
  if (citationRows.length > 0) {
    const { error: citationError } = await asService.from('draft_citations').insert(citationRows);
    if (citationError) {
      return json({ created: true, draft_id: draft.id, version, matches, warnings: [`draft created but citation binding failed: ${citationError.message}`] }, 207);
    }
  }

  await logAction(asService, callerId, 'memo_generated', {
    targetTable: 'drafts', targetId: draft.id,
    after: { matter_id, question, match_count: matches.length, citations_bound: citationRows.length, retrieval },
  });

  const warnings: string[] = [];
  const unverified = citationRows.filter((c) => c.status === 'unverified').length;
  const flagged = citationRows.filter((c) => c.status === 'flagged').length;
  if (unverified > 0) warnings.push(`${unverified} citation(s) bound from unverified corpus sources — resolve them in the Citation Inspector before export.`);
  if (flagged > 0) warnings.push(`${flagged} citation(s) bound from disputed corpus sources — flagged automatically, will block export until resolved.`);

  return json({ created: true, draft_id: draft.id, version, matches, citations_bound: citationRows.length, warnings });
});
