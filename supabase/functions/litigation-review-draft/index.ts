// litigation-review-draft
//
// POST { draft_id } -> { mismatches, missing_sections, overall_status }
//
// A "second pair of eyes" before export, complementing the existing
// citation inspector: that tool checks whether a draft's LEGAL citations
// are bound to real retrieved text; this one checks whether the draft's
// own FACTS (party names, court, case number, dates) actually match the
// matter's own record, and flags conventionally-expected sections that
// seem to be missing. It is not a legal-quality review and never touches
// citations -- if something looks like a citation problem, it is told to
// defer to the citation inspector rather than comment on it itself.
//
// Ephemeral like litigation-summarize: nothing is persisted, the result
// exists only in the response and an audit_log entry. Gated on
// export_matter, the same capability litigation-export-draft itself uses
// -- this is explicitly a pre-export check, not a drafting action.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';

const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  required: ['mismatches', 'missing_sections', 'overall_status'],
  properties: {
    mismatches: { type: 'ARRAY', items: { type: 'STRING' }, description: 'Each factual mismatch between the draft text and the matter record given, in Arabic, one sentence each. Empty array if none.' },
    missing_sections: { type: 'ARRAY', items: { type: 'STRING' }, description: 'Sections conventionally expected for this doc_type that appear to be missing, in Arabic. Empty array if none or if unsure.' },
    overall_status: { type: 'STRING', enum: ['consistent', 'minor_issues', 'needs_revision'] },
  },
};

const SYSTEM_INSTRUCTION =
  'You review an Egyptian legal draft for a lawyer, in Arabic, checking ONLY two things: (1) factual consistency between the draft text and the matter record given to you (party names, court, case number, dates) -- flag anything the draft states that contradicts the record; (2) structural completeness -- sections conventionally expected for a document of this type that seem to be missing, only when you are genuinely confident, never invented from nothing. ' +
  'You are NOT reviewing legal correctness, drafting quality, or citations -- a separate tool already checks citations. Do NOT add, invent, or evaluate any legal citation, article, or case reference yourself; if something looks like a citation problem, note only that it needs citation-inspector review, nothing more. Never invent a mismatch that is not actually supported by the matter record given.';

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'export_matter', 'draft_reviewed');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { draft_id } = body ?? {};
  if (!draft_id) return badRequest('draft_id is required');

  const { data: draft, error: draftErr } = await asService
    .from('drafts')
    .select('id, matter_id, doc_type, content_text, version')
    .eq('id', draft_id)
    .maybeSingle();
  if (draftErr) return json({ error: draftErr.message }, 500);
  if (!draft) return badRequest('draft not found');

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: draft.matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  if (!draft.content_text || !draft.content_text.trim()) {
    return json({ mismatches: [], missing_sections: [], overall_status: 'consistent', note: 'المسودة لا تحتوي على نص — لا يوجد ما يُراجع.' });
  }

  if (!GEMINI_API_KEY) {
    return json({ error: 'GEMINI_API_KEY is not configured for this project (Edge Function secret) — draft review cannot run until it is set' }, 502);
  }

  const [{ data: matter }, { data: parties }, { data: hearings }] = await Promise.all([
    asService.from('matters').select('matter_label, court, circuit, case_number, case_year, subject').eq('id', draft.matter_id).single(),
    asService.from('matter_parties').select('party_role, name, identifier').eq('matter_id', draft.matter_id),
    asService.from('hearings').select('session_date, next_session_date').eq('matter_id', draft.matter_id).order('session_date', { ascending: false }).limit(3),
  ]);

  const context =
    `نوع المستند: ${draft.doc_type}\n\n` +
    `سجل القضية (مصدر الحقيقة):\n${JSON.stringify(matter, null, 2)}\n\n` +
    `الأطراف المسجلون:\n${JSON.stringify(parties, null, 2)}\n\n` +
    `الجلسات الأخيرة/القادمة:\n${JSON.stringify(hearings, null, 2)}\n\n` +
    `نص المسودة المراد مراجعتها:\n${draft.content_text}`;

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
          generationConfig: { temperature: 0, responseMimeType: 'application/json', responseSchema: RESPONSE_SCHEMA },
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

  let review: { mismatches: string[]; missing_sections: string[]; overall_status: string };
  try {
    review = JSON.parse(textPart.text);
  } catch {
    return json({ error: 'model output was not valid JSON' }, 502);
  }

  await logAction(asService, callerId, 'draft_reviewed', {
    targetTable: 'drafts', targetId: draft_id,
    after: { overall_status: review.overall_status, mismatch_count: review.mismatches?.length ?? 0, missing_section_count: review.missing_sections?.length ?? 0 },
  });

  return json({ mismatches: review.mismatches ?? [], missing_sections: review.missing_sections ?? [], overall_status: review.overall_status });
});
