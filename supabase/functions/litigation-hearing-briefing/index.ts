// litigation-hearing-briefing
//
// POST { matter_id, hearing_id? } -> { draft_id, version, content_text, relevant_authorities, truncated_context }
//
// A pre-hearing prep aid: synthesizes the matter's own stored facts —
// parties, full hearing history, open deadlines, transcribed document
// text — into a structured briefing (procedural summary, key facts, open
// issues, prep points) a lawyer can read in five minutes before walking
// into court. Distinct from litigation-memo: a memo answers ONE legal
// question grounded in retrieved corpus law; a briefing has no single
// question — its job is to reconstruct "where does this case stand" from
// the matter's own record, the same grounding discipline as
// litigation-draft rather than litigation-research/-memo.
//
// CITATIONS: same rule as litigation-draft, same reasoning — the model is
// never given corpus passages to synthesize prose from, so it is
// explicitly forbidden from citing any law, real or invented, and told to
// insert "[يحتاج استشهاد قانوني]" wherever one would normally belong. A
// SEPARATE, best-effort fn_search_authorities lookup (using the matter's
// own subject as the query) is run alongside this and returned as raw,
// unsynthesized passages under relevant_authorities — the model never
// sees them and never claims credit for finding them. This keeps "the
// model wrote this narrative" and "the corpus happens to have something
// on this subject" strictly separate, so nothing here can be mistaken
// for a grounded legal citation the way a memo's citations are.
//
// Persisted as a drafts row (doc_type='hearing_briefing') for the same
// reason every other AI output in this build is: audit trail, version
// history, and it shows up in the matter's existing Drafts tab for free.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';
const EMBED_MODEL = Deno.env.get('GEMINI_EMBED_MODEL') || 'gemini-embedding-001';
const EMBED_DIMS = 1536;
const MAX_CONTEXT_CHARS = 60000;

const STAGE_LABELS: Record<string, string> = {
  first_instance: 'ابتدائي', appeal: 'استئناف', cassation: 'نقض', execution: 'تنفيذ',
};

const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  required: ['procedural_summary', 'key_facts', 'open_issues', 'prep_points'],
  properties: {
    procedural_summary: { type: 'STRING', description: 'How the case has proceeded so far — stage, hearing history, outcomes — in Arabic.' },
    key_facts: { type: 'ARRAY', items: { type: 'STRING' }, description: 'The facts of the matter most relevant to the upcoming hearing, in Arabic.' },
    open_issues: { type: 'ARRAY', items: { type: 'STRING' }, description: 'Unresolved procedural or factual issues (pending deadlines, unconfirmed extractions, missing documents), in Arabic.' },
    prep_points: { type: 'ARRAY', items: { type: 'STRING' }, description: 'Concrete things the lawyer should be ready to address at this hearing, in Arabic.' },
  },
};

const SYSTEM_INSTRUCTION =
  'You prepare a pre-hearing briefing for an Egyptian litigation lawyer, in Arabic — this is prep material for the lawyer to review before a hearing, not a filing. ' +
  'Ground every fact ONLY in the matter record given to you (parties, hearing history, deadlines, document excerpts) — never invent a date, outcome, or fact not present in that record. ' +
  'CITATIONS: no legal corpus passages are given to you here — do NOT cite any article, law, or case, real or invented. Wherever the briefing would normally reference a legal basis, insert the literal placeholder "[يحتاج استشهاد قانوني]" instead — not optional.';

async function embedQuery(text: string): Promise<number[] | null> {
  if (!GEMINI_API_KEY) return null;
  try {
    const resp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${EMBED_MODEL}:embedContent?key=${GEMINI_API_KEY}`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ model: `models/${EMBED_MODEL}`, content: { parts: [{ text }] }, taskType: 'RETRIEVAL_QUERY', outputDimensionality: EMBED_DIMS }),
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

  const capError = await requireCapability(asUser, asService, callerId, 'generate_draft', 'hearing_briefing_generated');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { matter_id, hearing_id } = body ?? {};
  if (!matter_id) return badRequest('matter_id is required');

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  if (!GEMINI_API_KEY) {
    return json({ error: 'GEMINI_API_KEY is not configured for this project (Edge Function secret) — briefing generation cannot run until it is set' }, 502);
  }

  const [{ data: matter }, { data: parties }, { data: hearings }, { data: deadlines }, { data: docs }, { data: targetHearing }] = await Promise.all([
    asService.from('matters').select('matter_label, court, circuit, case_number, case_year, matter_type, stage, subject').eq('id', matter_id).single(),
    asService.from('matter_parties').select('party_role, name, identifier').eq('matter_id', matter_id),
    asService.from('hearings').select('session_date, session_time, outcome, adjournment_reason, next_session_date').eq('matter_id', matter_id).order('session_date', { ascending: false }),
    asService.from('deadlines').select('trigger_event, computed_due_date, status').eq('matter_id', matter_id).order('computed_due_date', { ascending: true }),
    asService.from('documents').select('id, original_filename, document_pages(page_number, text_content)').eq('matter_id', matter_id),
    hearing_id ? asService.from('hearings').select('*').eq('id', hearing_id).eq('matter_id', matter_id).maybeSingle() : Promise.resolve({ data: null }),
  ]);
  if (hearing_id && !targetHearing) return badRequest('hearing not found on this matter');

  let context = `بيانات القضية:\n${JSON.stringify({ ...matter, stage: matter?.stage ? (STAGE_LABELS[matter.stage] || matter.stage) : null }, null, 2)}\n\n`;
  context += `الأطراف:\n${JSON.stringify(parties, null, 2)}\n\n`;
  if (targetHearing) context += `الجلسة المستهدفة لهذه الإحاطة:\n${JSON.stringify(targetHearing, null, 2)}\n\n`;
  context += `تاريخ الجلسات (الأحدث أولاً):\n${JSON.stringify(hearings, null, 2)}\n\n`;
  context += `المواعيد:\n${JSON.stringify(deadlines, null, 2)}\n\n`;
  context += 'مقتطفات من مستندات القضية:\n';
  let truncated = false;
  for (const d of docs || []) {
    for (const p of (d as any).document_pages || []) {
      if (!p.text_content) continue;
      const block = `\n[${d.original_filename ?? d.id} ص.${p.page_number}]\n${p.text_content}\n`;
      if (context.length + block.length > MAX_CONTEXT_CHARS) { truncated = true; break; }
      context += block;
    }
    if (truncated) break;
  }

  // Best-effort, unsynthesized corpus lookup — the model never sees these,
  // so they can never be misrepresented as something it "cited". Failure
  // here (no embedding available, no matches) never blocks the briefing.
  let relevantAuthorities: unknown[] = [];
  if (matter?.subject) {
    try {
      const queryEmbedding = await embedQuery(String(matter.subject));
      const { data: authMatches } = await asUser.rpc('fn_search_authorities', {
        p_query_text: String(matter.subject),
        p_query_embedding: queryEmbedding ? JSON.stringify(queryEmbedding) : null,
        p_match_count: 5,
      });
      relevantAuthorities = (authMatches ?? []).map((m: any) => ({
        title: m.title, citation: m.citation, chunk_ref: m.chunk_ref,
        authority_type: m.authority_type, verification_status: m.verification_status,
      }));
    } catch { /* best-effort only */ }
  }

  let apiResp: Response;
  try {
    apiResp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${GEMINI_API_KEY}`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: SYSTEM_INSTRUCTION }] },
          contents: [{ role: 'user', parts: [{ text: context }] }],
          generationConfig: { temperature: 0.3, responseMimeType: 'application/json', responseSchema: RESPONSE_SCHEMA },
        }),
      },
    );
  } catch (e) {
    return json({ error: `Gemini API request failed: ${e instanceof Error ? e.message : String(e)}` }, 502);
  }
  if (!apiResp.ok) {
    const errText = await apiResp.text().catch(() => '');
    return json({ error: `Gemini API returned ${apiResp.status}: ${errText.slice(0, 500)}` }, 502);
  }

  const apiJson = await apiResp.json();
  const blockReason = apiJson.promptFeedback?.blockReason;
  if (blockReason) return json({ error: `Gemini blocked the request: ${blockReason}` }, 502);

  const textPart = apiJson.candidates?.[0]?.content?.parts?.find((p: { text?: string }) => typeof p.text === 'string');
  if (!textPart) return json({ error: 'model did not return the expected JSON output' }, 502);

  let briefing: { procedural_summary: string; key_facts: string[]; open_issues: string[]; prep_points: string[] };
  try {
    briefing = JSON.parse(textPart.text);
  } catch {
    return json({ error: 'model output was not valid JSON' }, 502);
  }

  const contentText =
    `إحاطة تحضيرية للجلسة — ${matter?.matter_label ?? matter_id}\n\n` +
    `الملخص الإجرائي:\n${briefing.procedural_summary}\n\n` +
    `الوقائع الرئيسية:\n${(briefing.key_facts || []).map((f) => `• ${f}`).join('\n')}\n\n` +
    `مسائل مفتوحة:\n${(briefing.open_issues || []).map((f) => `• ${f}`).join('\n')}\n\n` +
    `نقاط الاستعداد:\n${(briefing.prep_points || []).map((f) => `• ${f}`).join('\n')}` +
    (relevantAuthorities.length > 0
      ? `\n\nمصادر قد تكون ذات صلة (استرجاع خام لم يلخصه النموذج — يجب التحقق منها يدوياً):\n${relevantAuthorities.map((a: any) => `• ${a.title} (${a.citation})`).join('\n')}`
      : '');

  const { data: existing } = await asService
    .from('drafts')
    .select('version')
    .eq('matter_id', matter_id)
    .eq('doc_type', 'hearing_briefing')
    .order('version', { ascending: false })
    .limit(1)
    .maybeSingle();
  const version = (existing?.version ?? 0) + 1;

  const { data: draft, error } = await asService
    .from('drafts')
    .insert({ matter_id, doc_type: 'hearing_briefing', version, content_text: contentText, status: 'ready_for_review', created_by: callerId })
    .select('id')
    .single();
  if (error) return json({ error: error.message }, 500);

  await logAction(asService, callerId, 'hearing_briefing_generated', {
    targetTable: 'drafts', targetId: draft.id,
    after: { matter_id, hearing_id: hearing_id ?? null, version, truncated_context: truncated, relevant_authority_count: relevantAuthorities.length },
  });

  return json({ draft_id: draft.id, version, content_text: contentText, relevant_authorities: relevantAuthorities, truncated_context: truncated });
});
