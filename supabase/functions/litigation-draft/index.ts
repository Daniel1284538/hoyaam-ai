// litigation-draft
//
// POST { matter_id, doc_type, template_id?, instructions? }
//
// Plan section 01, Tier B, "Drafting from your templates": a first draft
// built from the firm's own template (if given) and this matter's actual
// facts (parties, extracted fields, transcribed document text) — not a
// generic idea of how a memo should read.
//
// HONEST GAP, deliberate: authorities/authority_chunks (the legal corpus)
// is EMPTY — Phase 4 hasn't sourced any real Egyptian statute or
// Cassation-principle text yet, and this function will never fabricate
// one to fill the gap (plan section 03, "Fabricated citations" — the
// single most likely way a legal-AI project dies). So the model is
// explicitly instructed to cite NO legal authority and to mark every spot
// that would normally need one with a plain placeholder instead. Once a
// real corpus exists, this function should be extended to retrieve from
// authority_chunks and bind real citations — it does not do that yet.
//
// Provider: Gemini (gemini-3.6-flash by default), matching the rest of
// this build.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';
const MAX_CONTEXT_CHARS = 60000;

const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  required: ['title', 'body'],
  properties: {
    title: { type: 'STRING' },
    body: { type: 'STRING', description: 'Full draft text in Arabic, formal legal register, matching the template style if one was provided.' },
  },
};

const SYSTEM_INSTRUCTION =
  'You draft Egyptian litigation documents in Arabic for a lawyer to review — you are producing a FIRST DRAFT, not a final filing, and the lawyer signs everything before it goes anywhere. ' +
  'Ground every factual claim ONLY in the matter facts and document excerpts given to you — never invent facts, dates, or figures. ' +
  'CITATIONS: no legal corpus is available to you in this system — you have NOT been given any statute text or Court of Cassation ruling. Do NOT cite any article, law, or case, real or invented. Wherever the draft would normally need a legal citation, insert the literal placeholder "[يحتاج استشهاد قانوني]" instead. This is not optional — a fabricated citation in a legal draft is the single worst failure this system can produce.';

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'generate_draft', 'draft_generated');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { matter_id, doc_type, template_id, instructions } = body ?? {};
  if (!matter_id || !doc_type) return badRequest('matter_id and doc_type are required');

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  if (!GEMINI_API_KEY) {
    return json({ error: 'GEMINI_API_KEY is not configured for this project (Edge Function secret) — drafting cannot run until it is set' }, 502);
  }

  const [{ data: matter }, { data: parties }, { data: template }, { data: docs }] = await Promise.all([
    asService.from('matters').select('matter_label, court, circuit, case_number, case_year, matter_type, stage, subject').eq('id', matter_id).single(),
    asService.from('matter_parties').select('party_role, name, identifier').eq('matter_id', matter_id),
    template_id ? asService.from('templates').select('title, content_text').eq('id', template_id).single() : Promise.resolve({ data: null }),
    asService.from('documents').select('id, original_filename, document_pages(page_number, text_content)').eq('matter_id', matter_id),
  ]);

  let context = `بيانات القضية:\n${JSON.stringify(matter, null, 2)}\n\nالأطراف:\n${JSON.stringify(parties, null, 2)}\n\n`;
  if (template) context += `قالب المكتب (${template.title}):\n${template.content_text}\n\n`;
  if (instructions) context += `تعليمات إضافية:\n${instructions}\n\n`;
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

  let apiResp: Response;
  try {
    apiResp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${GEMINI_API_KEY}`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: SYSTEM_INSTRUCTION }] },
          contents: [{ role: 'user', parts: [{ text: `نوع المستند: ${doc_type}\n\n${context}` }] }],
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

  let draftOut: { title: string; body: string };
  try {
    draftOut = JSON.parse(textPart.text);
  } catch {
    return json({ error: 'model output was not valid JSON' }, 502);
  }

  const { data: existing } = await asService
    .from('drafts')
    .select('version')
    .eq('matter_id', matter_id)
    .eq('doc_type', doc_type)
    .order('version', { ascending: false })
    .limit(1)
    .maybeSingle();
  const version = (existing?.version ?? 0) + 1;

  const contentText = `${draftOut.title}\n\n${draftOut.body}`;
  const { data: draft, error } = await asService
    .from('drafts')
    .insert({ matter_id, doc_type, template_id: template_id || null, version, content_text: contentText, status: 'ready_for_review', created_by: callerId })
    .select('id')
    .single();
  if (error) return json({ error: error.message }, 500);

  await logAction(asService, callerId, 'draft_generated', {
    targetTable: 'drafts', targetId: draft.id, after: { matter_id, doc_type, version, truncated_context: truncated },
  });

  return json({ draft_id: draft.id, version, content_text: contentText, truncated_context: truncated });
});
