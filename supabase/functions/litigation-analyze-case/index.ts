// litigation-analyze-case
//
// POST { matter_id } -> { analysis, case_context, matches, web_sources,
//   web_search_queries, warnings, retrieval, note?, error? }
//
// Manual, on-demand case analysis, run from the Overview tab. Deliberately
// gated on the matter's subject being filled in — this function exists
// because a matter was created without one, and an analysis run on an
// empty subject would have nothing real to say about the case at all.
//
// THREE grounding sources, each licensed for a different part of the
// output — never interchangeable:
//
//   1. THIS MATTER'S OWN DOCUMENTS. Transcribed document_pages text (same
//      source litigation-chronology already reads) — the model is told
//      these are the only source of case FACTS; it must not state anything
//      about this case that isn't in them. If no documents have been
//      uploaded/extracted yet, the analysis still runs from the subject
//      alone, but says so explicitly rather than pretending it reviewed a
//      file that doesn't exist.
//
//   2. THE LEGAL AUTHORITIES CORPUS. Same fn_search_authorities hybrid
//      search litigation-research uses, queried with the subject text —
//      the model is told these are the only source it may cite LAW from.
//      Same repealed-law exclusion, same [UNVERIFIED]/[DISPUTED]/[FIQH]
//      flagging, same rule as everywhere else: no statute or citation that
//      isn't literally present in a retrieved passage.
//
//   3. LIVE WEB SEARCH, for the roadmap section only. Gemini's built-in
//      Search grounding tool (`google_search`) — a REAL retrieval
//      mechanism, not the model answering from its own memory: every web
//      source it draws on comes back in `groundingMetadata` as an actual
//      URL the API says it consulted, which is what this function reports
//      back as `web_sources` — not the model's own unverifiable claim
//      about what it checked. It is explicitly licensed for the roadmap/
//      next-steps section only (general procedural guidance, how similar
//      matters are typically approached), never for stating a fact about
//      THIS case or naming a statute as binding law — that stays SOURCE
//      2's job alone. Every web-drawn claim must be inline-labeled
//      [بحث خارجي] so it's never mistaken for firm-verified corpus law.
//      Search grounding is something the model chooses to invoke per
//      request, not guaranteed on every call — `web_sources` can come
//      back empty even when the tool is available, and that's reported
//      honestly rather than papered over.
//
// If NEITHER of the first two sources has anything (no documents AND no
// matching authorities), the model is never called — there would be
// nothing to ground an answer in except the bare subject sentence, the
// model's own memory, and an open-ended web search with no case facts to
// anchor it, which is exactly the fabrication risk this system refuses
// everywhere else (see litigation-research's "zero matches -> no model
// call" rule, same principle here). Web search augments an analysis that
// already has real grounding; it is deliberately not enough on its own to
// unlock one.
//
// Not persisted — regenerate on demand, same choice as litigation-
// chronology/-hearing-briefing/-summarize (case documents and the corpus
// both change over time; a stored analysis would silently go stale).
// Logged to audit_log for traceability.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, serviceClient, json, badRequest, corsPreflight } from './_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';
const EMBED_MODEL = Deno.env.get('GEMINI_EMBED_MODEL') || 'gemini-embedding-001';
const EMBED_DIMS = 1536;
const MAX_DOC_CHARS = 60000;

type Match = {
  chunk_id: string; authority_id: string; chunk_text: string; chunk_ref: string | null;
  title: string; citation: string; authority_type: string; madhhab: string | null;
  verification_status: string; effective_date: string | null; score: number;
};

const SYSTEM_INSTRUCTION =
  'You write a case analysis for an Egyptian lawyer, in Arabic, from up to three separate sources — never mix what each one licenses you to say, and SOURCE 3 is never equal in authority to SOURCE 1 or SOURCE 2. ' +
  'SOURCE 1, transcribed pages from the uploaded documents belonging to this specific case (if any): treat these as the ONLY source of facts about this case. Never state a fact about this case — a date, an action by a party, an amount, an outcome — that is not present in these pages. If no pages were provided, say explicitly that the analysis rests on the stated subject only, not on a document review. ' +
  'SOURCE 2, passages retrieved from the legal-authorities corpus of the firm (if any): treat these as the ONLY source you may cite law from. Never name a statute, article number, or case citation that is not literally present in one of these passages. If no passages were provided, say explicitly that no matching law was found in the corpus, and do not name any statute or article from memory. Passages marked [UNVERIFIED] have not been checked against the official source; if you rely on one, say so. Passages marked [DISPUTED] may be wrong; flag any reliance on one. Passages marked [FIQH: school] are doctrine of one school, not enacted law — never present as binding statute. ' +
  'SOURCE 3, live web search (a Google Search tool is available to you): use it ONLY to inform part (3), the roadmap — general procedural guidance, how similar matters are typically approached, practical next steps. NEVER use it to state a fact about this specific case, and NEVER use it to name a statute or article as binding law on this matter — that is the role of SOURCE 2 alone. Any claim drawn from web search must be marked inline with the literal tag "[بحث خارجي]" immediately after it, so it is never mistaken for firm-verified corpus law or a fact from the case file. ' +
  'Structure your analysis in four short parts: (1) a factual summary grounded strictly in Source 1 (or a note that none was given); (2) the potentially applicable law grounded strictly in Source 2 (or a note that none was found); (3) a suggested roadmap — concrete next steps to move the case forward, grounded in Source 1 facts and Source 2 law, optionally informed by Source 3 web search where useful (tag every web-drawn claim [بحث خارجي] as instructed above) — state plainly that this is a draft starting point for the lawyer to evaluate, not legal advice; (4) open questions — what would need to be established or what documents are still missing to assess the case properly.';

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

  const capError = await requireCapability(asUser, asService, callerId, 'run_research', 'case_analysis_generated');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { matter_id } = body ?? {};
  if (!matter_id) return badRequest('matter_id is required');

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  const { data: matter, error: matterError } = await asService
    .from('matters')
    .select('matter_label, subject, court, case_number, case_year, stage, matter_type')
    .eq('id', matter_id)
    .single();
  if (matterError || !matter) return badRequest('no such matter');

  if (!matter.subject || !String(matter.subject).trim()) {
    return badRequest('subject is required before running a case analysis — add it from the Overview tab first');
  }
  const subject = String(matter.subject).trim();

  const { data: docs, error: docsError } = await asService
    .from('documents')
    .select('id, original_filename, document_pages(page_number, text_content)')
    .eq('matter_id', matter_id);
  if (docsError) return json({ error: docsError.message }, 500);

  const pages: { document_id: string; document_name: string | null; page_number: number; text_content: string }[] = [];
  const documentIds = new Set<string>();
  for (const d of docs || []) {
    for (const p of (d as any).document_pages || []) {
      if (p.text_content) {
        pages.push({ document_id: d.id, document_name: d.original_filename, page_number: p.page_number, text_content: p.text_content });
        documentIds.add(d.id);
      }
    }
  }

  let docBlock = '';
  let truncated = false;
  for (const p of pages) {
    const block = `\n[document_id=${p.document_id} document_name=${p.document_name ?? ''} page=${p.page_number}]\n${p.text_content}\n`;
    if (docBlock.length + block.length > MAX_DOC_CHARS) { truncated = true; break; }
    docBlock += block;
  }

  const asOf = new Date().toISOString().slice(0, 10);
  const queryEmbedding = await embedQuery(subject);
  const { data: matchData, error: matchError } = await asUser.rpc('fn_search_authorities', {
    p_query_text: subject,
    p_query_embedding: queryEmbedding ? JSON.stringify(queryEmbedding) : null,
    p_match_count: 10,
    p_as_of: asOf,
    p_authority_types: null,
    p_include_unverified: true,
  });
  if (matchError) return json({ error: matchError.message }, 500);

  const matches = (matchData ?? []) as Match[];
  const retrieval = queryEmbedding ? 'hybrid (dense + arabic fts)' : 'arabic fts only (no query embedding available)';
  const caseContext = { pages_used: pages.length, documents_used: documentIds.size, truncated };

  if (pages.length === 0 && matches.length === 0) {
    return json({
      analysis: null,
      case_context: caseContext,
      matches: [],
      web_sources: [],
      web_search_queries: [],
      warnings: [],
      retrieval,
      note: 'لا توجد مستندات مستخرجة لهذه القضية ولا نتائج مطابقة في المصادر القانونية — لا يوجد ما يُبنى عليه تحليل حقيقي غير الموضوع نفسه، ولن يُطلب من النموذج الإجابة من ذاكرته أو البحث المفتوح دون سياق حقيقي. ارفع مستندات القضية أو ثبّت مصادر قانونية أولاً.',
    });
  }

  const unverified = matches.filter((m) => m.verification_status !== 'human_verified');
  const disputed = matches.filter((m) => m.verification_status === 'disputed');
  const fiqh = matches.filter((m) => m.authority_type === 'fiqh_doctrine');

  const warnings: string[] = [];
  if (pages.length === 0) warnings.push('لا توجد مستندات مستخرجة لهذه القضية بعد — التحليل مبني على الموضوع المُدخل فقط، وليس على مراجعة ملف القضية.');
  if (matches.length === 0) warnings.push('لم يُعثر على مصادر قانونية مطابقة في المكتبة القانونية لهذا الموضوع.');
  if (unverified.length > 0) warnings.push(`${unverified.length} من ${matches.length} مقاطع قانونية غير محقّقة من مصدرها الرسمي بعد.`);
  if (disputed.length > 0) warnings.push(`${disputed.length} مقطع/مقاطع مُعلَّمة كمتنازع عليها.`);
  if (fiqh.length > 0) warnings.push(`${fiqh.length} مقطع/مقاطع فقهية — اجتهاد مذهبي، وليست قانوناً نافذاً.`);
  if (truncated) warnings.push('تم اقتطاع بعض نصوص المستندات لتجاوز الحد الأقصى — التحليل قد يكون غير كامل.');

  if (!GEMINI_API_KEY) {
    return json({ analysis: null, case_context: caseContext, matches, web_sources: [], web_search_queries: [], warnings, retrieval, error: 'GEMINI_API_KEY is not configured — grounding material was found but cannot be synthesized into an analysis' }, 502);
  }

  const authorityBlock = matches.map((m, i) => {
    const flags = [
      m.verification_status !== 'human_verified' ? '[UNVERIFIED]' : '',
      m.verification_status === 'disputed' ? '[DISPUTED]' : '',
      m.authority_type === 'fiqh_doctrine' ? `[FIQH: ${m.madhhab ?? 'school unspecified'}]` : '',
    ].filter(Boolean).join(' ');
    return `[${i + 1}] ${flags} ${m.title} (${m.citation})${m.chunk_ref ? ` — ${m.chunk_ref}` : ''}\n${m.chunk_text}`;
  }).join('\n\n');

  const matterMeta = [
    `العنوان: ${matter.matter_label}`,
    matter.court ? `المحكمة: ${matter.court}` : null,
    matter.case_number ? `رقم القضية: ${matter.case_number}${matter.case_year ? '/' + matter.case_year : ''}` : null,
    matter.stage ? `المرحلة: ${matter.stage}` : null,
    matter.matter_type ? `نوع القضية: ${matter.matter_type}` : null,
  ].filter(Boolean).join('\n');

  const prompt =
    `بيانات القضية:\n${matterMeta}\n\nالموضوع كما أدخله المحامي:\n${subject}\n\n` +
    `SOURCE 1 — صفحات مستندات القضية (${pages.length} صفحة):\n${docBlock || '(لا توجد مستندات مستخرجة)'}\n\n` +
    `SOURCE 2 — مقاطع من المصادر القانونية (${matches.length} مقطع):\n${authorityBlock || '(لا توجد نتائج مطابقة)'}`;

  let apiResp: Response;
  try {
    apiResp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${GEMINI_API_KEY}`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: SYSTEM_INSTRUCTION }] },
          contents: [{ role: 'user', parts: [{ text: prompt }] }],
          tools: [{ google_search: {} }],
          generationConfig: { temperature: 0 },
        }),
      },
    );
  } catch (e) {
    return json({ analysis: null, case_context: caseContext, matches, web_sources: [], web_search_queries: [], warnings, retrieval, error: `Gemini API request failed: ${e instanceof Error ? e.message : String(e)}` }, 502);
  }
  if (!apiResp.ok) {
    const errText = await apiResp.text().catch(() => '');
    return json({ analysis: null, case_context: caseContext, matches, web_sources: [], web_search_queries: [], warnings, retrieval, error: `Gemini API returned ${apiResp.status}: ${errText.slice(0, 500)}` }, 502);
  }

  const apiJson = await apiResp.json();
  const blockReason = apiJson.promptFeedback?.blockReason;
  if (blockReason) return json({ analysis: null, case_context: caseContext, matches, web_sources: [], web_search_queries: [], warnings, retrieval, error: `Gemini blocked the request: ${blockReason}` }, 502);

  const candidate = apiJson.candidates?.[0];
  const analysis = candidate?.content?.parts?.find((p: { text?: string }) => typeof p.text === 'string')?.text ?? null;

  // groundingMetadata is the API's own record of what the search tool
  // actually consulted — not the model's self-report — so this is real,
  // not something the model could fabricate. webSearchQueries can be
  // present with an empty groundingChunks list (a search that found
  // nothing usable); both are reported honestly rather than merged away.
  const groundingMetadata = candidate?.groundingMetadata;
  const webSearchQueries: string[] = Array.isArray(groundingMetadata?.webSearchQueries) ? groundingMetadata.webSearchQueries : [];
  const webSources = (Array.isArray(groundingMetadata?.groundingChunks) ? groundingMetadata.groundingChunks : [])
    .map((c: any) => ({ uri: c?.web?.uri, title: c?.web?.title }))
    .filter((s: { uri?: string }) => !!s.uri);

  if (webSources.length > 0) {
    warnings.push(`استُخدم بحث خارجي عبر الإنترنت لقسم خطة العمل فقط (${webSources.length} مصدر) — هذه مصادر خارجية غير موثّقة من المكتب، تحقق منها بنفسك قبل الاعتماد عليها.`);
  }

  await asService.from('audit_log').insert({
    actor_id: callerId, action: 'case_analysis_generated', success: true,
    target_table: 'matters', target_id: matter_id,
    after: { pages_used: pages.length, documents_used: documentIds.size, match_count: matches.length, truncated, web_source_count: webSources.length },
  });

  return json({
    analysis,
    case_context: caseContext,
    retrieval,
    warnings,
    web_sources: webSources,
    web_search_queries: webSearchQueries,
    matches: matches.map((m) => ({
      id: m.chunk_id, title: m.title, citation: m.citation, chunk_ref: m.chunk_ref,
      authority_type: m.authority_type, madhhab: m.madhhab, verification_status: m.verification_status, score: m.score,
    })),
  });
});
