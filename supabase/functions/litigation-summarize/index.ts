// litigation-summarize
//
// POST { matter_id, document_id? } -> { scope, document_id?, summary,
//   key_points, flags, truncated_context } | { scope, summary: null, note }
//
// Ephemeral executive summary — of one document, or of the case-so-far —
// built only from text this system actually has: a document's own OCR'd
// text, or (for a whole-case summary) the matter's stored record (parties,
// hearing history, deadlines, every document's transcribed text). Unlike
// litigation-draft/-memo/-hearing-briefing, nothing here is persisted as a
// drafts row — this is a read aid, not a work product, so it is returned
// directly and only logged to audit_log for traceability. Gated on
// run_research (the same capability litigation-research and
// litigation-search-archive use) rather than generate_draft, since this
// produces no filing and creates no draft the practice-review workflow
// needs to track.
//
// FABRICATION GUARD, two shapes depending on scope:
//   - Whole-case summary: the model is given no corpus law at all (same
//     as litigation-hearing-briefing), so it is flatly forbidden from
//     citing any article/law/case and must insert the literal placeholder
//     "[يحتاج استشهاد قانوني]" wherever one would normally belong.
//   - Single-document summary: the source text itself may already quote
//     law (e.g. a judgment citing articles) — the model may repeat a
//     citation ONLY if it appears verbatim in the given OCR text, and must
//     use the same placeholder for anything it would otherwise be tempted
//     to add from its own memory.
// Either way: if there is no transcribed text to summarize (a document
// with no OCR'd pages yet), the model is never called — this mirrors
// litigation-research's "zero matches -> no model call" rule exactly.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';
const MAX_CONTEXT_CHARS = 60000;

const STAGE_LABELS: Record<string, string> = {
  first_instance: 'ابتدائي', appeal: 'استئناف', cassation: 'نقض', execution: 'تنفيذ',
};

const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  required: ['summary', 'key_points', 'flags'],
  properties: {
    summary: { type: 'STRING', description: 'A concise executive summary in Arabic, grounded only in the text given.' },
    key_points: { type: 'ARRAY', items: { type: 'STRING' }, description: 'The most important facts/points, in Arabic.' },
    flags: { type: 'ARRAY', items: { type: 'STRING' }, description: 'Ambiguities, gaps, illegible sections, or open risks worth a lawyer\'s attention, in Arabic. Empty array if none.' },
  },
};

const SYSTEM_INSTRUCTION_DOCUMENT =
  'You summarize ONE Egyptian legal document for a lawyer, in Arabic, from its OCR-extracted text only — never from anything else you know about this case or about Egyptian law. ' +
  'CITATIONS: you may repeat a law/article/case citation ONLY if it appears literally, verbatim, in the text given to you. Never introduce a citation that is not in the source text. Wherever you would otherwise reference a legal basis not literally present, insert the placeholder "[يحتاج استشهاد قانوني]" instead.';

const SYSTEM_INSTRUCTION_CASE =
  'You write an executive summary of a case-so-far for an Egyptian litigation lawyer, in Arabic, grounded ONLY in the matter record given to you (parties, hearing history, deadlines, document excerpts) — never invent a fact, date, or outcome not present in that record. ' +
  'CITATIONS: no legal corpus passages are given to you — do NOT cite any article, law, or case, real or invented. Wherever a legal basis would normally be referenced, insert the literal placeholder "[يحتاج استشهاد قانوني]" instead — not optional.';

async function callGemini(systemInstruction: string, userText: string): Promise<{ summary: string; key_points: string[]; flags: string[] } | Response> {
  let apiResp: Response;
  try {
    apiResp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${GEMINI_API_KEY}`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: systemInstruction }] },
          contents: [{ role: 'user', parts: [{ text: userText }] }],
          generationConfig: { temperature: 0.2, responseMimeType: 'application/json', responseSchema: RESPONSE_SCHEMA },
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
  try {
    return JSON.parse(textPart.text);
  } catch {
    return json({ error: 'model output was not valid JSON' }, 502);
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

  const capError = await requireCapability(asUser, asService, callerId, 'run_research', 'summary_generated');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { matter_id, document_id } = body ?? {};
  if (!matter_id) return badRequest('matter_id is required');

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  if (!GEMINI_API_KEY) {
    return json({ error: 'GEMINI_API_KEY is not configured for this project (Edge Function secret) — summary generation cannot run until it is set' }, 502);
  }

  // ---------- single-document summary ----------
  if (document_id) {
    const { data: doc, error: docErr } = await asService
      .from('documents')
      .select('id, original_filename, ocr_status, matter_id')
      .eq('id', document_id)
      .maybeSingle();
    if (docErr) return json({ error: docErr.message }, 500);
    if (!doc || doc.matter_id !== matter_id) return badRequest('document not found on this matter');

    const { data: pages } = await asService
      .from('document_pages')
      .select('page_number, text_content')
      .eq('document_id', document_id)
      .order('page_number', { ascending: true });
    const textPages = (pages || []).filter((p) => p.text_content && String(p.text_content).trim());

    if (textPages.length === 0) {
      return json({
        scope: 'document', document_id, summary: null, key_points: [], flags: [],
        note: 'لا يوجد نص مستخرج (OCR) لهذا المستند بعد — لا يمكن تلخيصه. لم يُطلب من النموذج التلخيص من العدم.',
      });
    }

    let context = `اسم المستند: ${doc.original_filename ?? document_id}\n\nالنص المستخرج (OCR):\n`;
    let truncated = false;
    for (const p of textPages) {
      const block = `\n[ص.${p.page_number}]\n${p.text_content}\n`;
      if (context.length + block.length > MAX_CONTEXT_CHARS) { truncated = true; break; }
      context += block;
    }

    const result = await callGemini(SYSTEM_INSTRUCTION_DOCUMENT, context);
    if (result instanceof Response) return result;

    await logAction(asService, callerId, 'document_summary_generated', {
      targetTable: 'documents', targetId: document_id,
      after: { matter_id, truncated_context: truncated },
    });

    return json({ scope: 'document', document_id, summary: result.summary, key_points: result.key_points, flags: result.flags, truncated_context: truncated });
  }

  // ---------- whole-case summary ----------
  const [{ data: matter }, { data: parties }, { data: hearings }, { data: deadlines }, { data: docs }] = await Promise.all([
    asService.from('matters').select('matter_label, court, circuit, case_number, case_year, matter_type, stage, subject').eq('id', matter_id).single(),
    asService.from('matter_parties').select('party_role, name, identifier').eq('matter_id', matter_id),
    asService.from('hearings').select('session_date, session_time, outcome, adjournment_reason, next_session_date').eq('matter_id', matter_id).order('session_date', { ascending: false }),
    asService.from('deadlines').select('trigger_event, computed_due_date, status').eq('matter_id', matter_id).order('computed_due_date', { ascending: true }),
    asService.from('documents').select('id, original_filename, document_pages(page_number, text_content)').eq('matter_id', matter_id),
  ]);

  let context = `بيانات القضية:\n${JSON.stringify({ ...matter, stage: matter?.stage ? (STAGE_LABELS[matter.stage] || matter.stage) : null }, null, 2)}\n\n`;
  context += `الأطراف:\n${JSON.stringify(parties, null, 2)}\n\n`;
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

  const result = await callGemini(SYSTEM_INSTRUCTION_CASE, context);
  if (result instanceof Response) return result;

  await logAction(asService, callerId, 'case_summary_generated', {
    targetTable: 'matters', targetId: matter_id,
    after: { truncated_context: truncated },
  });

  return json({ scope: 'case', summary: result.summary, key_points: result.key_points, flags: result.flags, truncated_context: truncated });
});
