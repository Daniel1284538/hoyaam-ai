// litigation-memo-web-research
//
// POST { draft_id } -> { question, web_sources, web_search_queries, warnings, error? }
//
// A legal memo's own Issue/Rule/Application/Conclusion cite ONLY the
// firm's own vetted corpus (litigation-memo — see that function's header:
// if retrieval finds zero matches, it refuses to even call the model,
// specifically so a memo can never cite something no one at the firm has
// checked). That guarantee is the whole point and stays untouched here.
//
// This is a deliberately SEPARATE, optional add-on: supplementary live
// web search on the memo's own legal question, for general context only
// — never a citation, never merged into the memo's content_text or its
// draft_citations. Same three-way split litigation-analyze-case already
// uses for its roadmap section (this matter's documents / the firm's
// corpus / live web), applied to the "live web" leg alone, for exactly
// the same reason: `google_search` is a real retrieval tool, so
// groundingMetadata reports back an actual URL the API says it consulted
// — not the model's own unverifiable claim about what it checked.
//
// Not persisted server-side, on purpose — same choice as
// litigation-analyze-case/-chronology/-hearing-briefing/-summarize: the
// live web changes over time, so a stored result would silently go
// stale. The client caches the response itself (with a generated-at
// timestamp) and re-calls this on request, same "cache with a date,
// reload manually" pattern the case-analysis panel already uses.
//
// draft_id (not matter_id + question) is the only input on purpose: the
// question a memo was generated from is already the first line of its
// own content_text (see litigation-memo's contentText composition), so
// this reads it back from the stored draft instead of asking the caller
// to remember and resend it correctly.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, serviceClient, json, badRequest, corsPreflight } from './_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';

const ISSUE_MARKER = '\n\nالمسألة القانونية:\n';

const SYSTEM_INSTRUCTION =
  'You research general, publicly-available context for an Egyptian legal question, for a lawyer who has already drafted their own memo grounded in their firm\'s verified sources. ' +
  'This is supplementary background only — how similar questions are typically approached, general procedural context, commentary — never a substitute for the firm\'s own citations. ' +
  'Never state a statute, article number, or case citation as if it were binding law; if you mention one, make clear it is from external web content, not verified by the firm. ' +
  'Write in Arabic.';

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'generate_draft', 'memo_web_research_requested');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { draft_id } = body ?? {};
  if (!draft_id) return badRequest('draft_id is required');

  // asUser, not asService — drafts_select's RLS policy (can_access_matter)
  // is the access check here, same as reading the memo itself would be.
  const { data: draft, error: draftError } = await asUser
    .from('drafts')
    .select('id, matter_id, doc_type, content_text')
    .eq('id', draft_id)
    .eq('doc_type', 'legal_memo')
    .maybeSingle();
  if (draftError) return json({ error: draftError.message }, 500);
  if (!draft) return badRequest('memo not found, or no access to its matter');

  const markerIdx = String(draft.content_text ?? '').indexOf(ISSUE_MARKER);
  const question = markerIdx >= 0 ? draft.content_text.slice(0, markerIdx).trim() : String(draft.content_text ?? '').trim();
  if (!question) return badRequest('could not recover the original question from this memo');

  if (!GEMINI_API_KEY) {
    return json({ question, web_sources: [], web_search_queries: [], warnings: [], error: 'GEMINI_API_KEY is not configured for this project (Edge Function secret)' }, 502);
  }

  const { data: matter } = await asService
    .from('matters')
    .select('matter_label, matter_type')
    .eq('id', draft.matter_id)
    .maybeSingle();

  const prompt =
    `نوع القضية: ${matter?.matter_type ?? 'غير محدد'}\nعنوان القضية: ${matter?.matter_label ?? 'غير محدد'}\n\n` +
    `المسألة القانونية التي تناولتها المذكرة (مصاغة بالفعل من المصادر الموثّقة للمكتب):\n${question}\n\n` +
    `ابحث عبر الإنترنت عن سياق أو ممارسات عامة ذات صلة بهذه المسألة لدعم المحامي بمعلومات تكميلية — غير ملزمة وغير موثّقة من المكتب.`;

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
    return json({ question, web_sources: [], web_search_queries: [], warnings: [], error: `Gemini API request failed: ${e instanceof Error ? e.message : String(e)}` }, 502);
  }
  if (!apiResp.ok) {
    const errText = await apiResp.text().catch(() => '');
    return json({ question, web_sources: [], web_search_queries: [], warnings: [], error: `Gemini API returned ${apiResp.status}: ${errText.slice(0, 500)}` }, 502);
  }

  const apiJson = await apiResp.json();
  const blockReason = apiJson.promptFeedback?.blockReason;
  if (blockReason) return json({ question, web_sources: [], web_search_queries: [], warnings: [], error: `Gemini blocked the request: ${blockReason}` }, 502);

  const candidate = apiJson.candidates?.[0];
  const commentary = candidate?.content?.parts?.find((p: { text?: string }) => typeof p.text === 'string')?.text ?? null;

  // Same as litigation-analyze-case: groundingMetadata is the API's own
  // record of what the search tool actually consulted, not the model's
  // self-report — real, not something the model could fabricate.
  const groundingMetadata = candidate?.groundingMetadata;
  const webSearchQueries: string[] = Array.isArray(groundingMetadata?.webSearchQueries) ? groundingMetadata.webSearchQueries : [];
  const webSources = (Array.isArray(groundingMetadata?.groundingChunks) ? groundingMetadata.groundingChunks : [])
    .map((c: { web?: { uri?: string; title?: string } }) => ({ uri: c?.web?.uri, title: c?.web?.title }))
    .filter((s: { uri?: string }) => !!s.uri);

  const warnings: string[] = [];
  if (webSources.length > 0) {
    warnings.push(`بحث خارجي تكميلي (${webSources.length} مصدر) — مصادر غير موثّقة من المكتب، تحقق منها بنفسك قبل الاعتماد عليها. لا تُستخدم كاستشهاد في المذكرة.`);
  }

  await asService.from('audit_log').insert({
    actor_id: callerId, action: 'memo_web_research_generated', success: true,
    target_table: 'drafts', target_id: draft_id,
    after: { matter_id: draft.matter_id, web_source_count: webSources.length },
  });

  return json({
    question,
    commentary,
    web_sources: webSources,
    web_search_queries: webSearchQueries,
    warnings,
  });
});
