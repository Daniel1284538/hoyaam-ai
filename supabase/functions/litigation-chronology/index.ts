// litigation-chronology
//
// POST { matter_id } -> { events: [{ date, description, source_document_id, source_page_number }] }
//
// Plan section 01, "Case chronology": a dated timeline assembled from the
// file, every line carrying a page reference back to the document it came
// from. Computed on demand from already-transcribed document_pages (no
// re-OCR, no image upload) — nothing is persisted, so re-running after
// new documents land just produces a fresh timeline.
//
// Provider: Gemini (gemini-3.6-flash by default), matching legal-extract.
// Forced JSON via responseSchema; the model is explicitly told never to
// invent a date or event not supported by the transcribed text, and every
// event must carry the source document/page it came from — same
// traceability requirement as extraction, not a narrative summary that
// can drift from the source.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';
const MAX_CHARS = 100000;

const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  required: ['events'],
  properties: {
    events: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        required: ['description', 'source_document_id', 'source_page_number'],
        properties: {
          date: { type: 'STRING', nullable: true, description: 'ISO date (YYYY-MM-DD) if the text states or clearly implies one; otherwise null.' },
          description: { type: 'STRING' },
          source_document_id: { type: 'STRING' },
          source_page_number: { type: 'INTEGER' },
        },
      },
    },
  },
};

const SYSTEM_INSTRUCTION =
  'You build a dated chronology of an Egyptian litigation case file from the provided page transcripts. Every event must cite the exact document_id and page_number (given in each block header) it came from. If a precise date is not stated or clearly implied by the text, set date to null but still include the event with its page reference. Do NOT invent dates or events not supported by the text — this chronology feeds a lawyer\'s case review and a fabricated entry is worse than a missing one.';

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'run_research', 'chronology_generated');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { matter_id } = body ?? {};
  if (!matter_id) return badRequest('matter_id is required');

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  if (!GEMINI_API_KEY) {
    return json({ error: 'GEMINI_API_KEY is not configured for this project (Edge Function secret) — chronology cannot run until it is set' }, 502);
  }

  const { data: docs, error: docsError } = await asService
    .from('documents')
    .select('id, original_filename, document_pages(page_number, text_content)')
    .eq('matter_id', matter_id);
  if (docsError) return json({ error: docsError.message }, 500);

  const pages: { document_id: string; document_name: string | null; page_number: number; text_content: string }[] = [];
  for (const d of docs || []) {
    for (const p of (d as any).document_pages || []) {
      if (p.text_content) pages.push({ document_id: d.id, document_name: d.original_filename, page_number: p.page_number, text_content: p.text_content });
    }
  }
  if (pages.length === 0) {
    return badRequest('no extracted document text yet for this matter — upload and extract documents first');
  }

  let combined = '';
  let truncated = false;
  for (const p of pages) {
    const block = `\n[document_id=${p.document_id} document_name=${p.document_name ?? ''} page=${p.page_number}]\n${p.text_content}\n`;
    if (combined.length + block.length > MAX_CHARS) { truncated = true; break; }
    combined += block;
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
          contents: [{ role: 'user', parts: [{ text: combined }] }],
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

  let parsed: { events: { date: string | null; description: string; source_document_id: string; source_page_number: number }[] };
  try {
    parsed = JSON.parse(textPart.text);
  } catch {
    return json({ error: 'model output was not valid JSON' }, 502);
  }

  const events = (parsed.events || []).slice().sort((a, b) => {
    if (!a.date && !b.date) return 0;
    if (!a.date) return 1;
    if (!b.date) return -1;
    return a.date.localeCompare(b.date);
  });

  await asService.from('audit_log').insert({
    actor_id: callerId, action: 'chronology_generated', success: true,
    target_table: 'matters', target_id: matter_id,
    after: { event_count: events.length, truncated },
  });

  return json({ events, truncated, pages_used: pages.length });
});
