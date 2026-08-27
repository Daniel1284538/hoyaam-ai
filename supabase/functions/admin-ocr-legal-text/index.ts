// admin-ocr-legal-text
//
// POST { file_base64, mime_type } -> { text, pages }
//
// OCR-assist for the corpus "Add Source" dialog. Adding a legal authority
// was manual-text-entry only — no way to upload a scan of the statute and
// have the text pre-filled, unlike case documents (legal-ingest/
// legal-extract). This closes that gap WITHOUT weakening the corpus's own
// verification discipline (CHANGELOG: "loading unverified bulk text would
// be worse than an empty corpus"): it is a pure transcription utility,
// nothing more. It does not touch `authorities`/`authority_chunks`, does
// not persist the uploaded file anywhere (no Storage bucket involved —
// unlike case documents, a corpus source's original scan isn't a record
// this system needs to keep), and does not set verification_status —
// litigation-manage-authority still owns writing the corpus, still always
// starts a submission as machine_ingested, and a human still has to
// review/edit the transcribed text in the form before saving, exactly as
// before. This function's only job is turning a scan into a starting
// draft of text instead of a blank textarea.
//
// Transcription only, not extraction — no title/citation/date guessing,
// unlike legal-extract's structured field extraction for case documents.
// A legal source's citation metadata is exactly the part a human must
// supply and verify, not something to auto-fill from a model's guess.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';
const MAX_FILE_BASE64_CHARS = 20_000_000; // ~15MB decoded, generous for a scanned statute

const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  required: ['pages'],
  properties: {
    pages: {
      type: 'ARRAY',
      description: 'One entry per page, in order, starting at 1.',
      items: {
        type: 'OBJECT',
        required: ['page_number', 'text_content'],
        properties: {
          page_number: { type: 'INTEGER' },
          text_content: { type: 'STRING', description: 'Full transcribed text of this page, exactly as written.' },
        },
      },
    },
  },
};

const SYSTEM_INSTRUCTION =
  'You transcribe scanned legal source text (statutes, court judgments, regulations, fiqh texts) exactly as written. Transcribe verbatim — do not translate, summarize, paraphrase, correct, or comment on the text. Preserve article/paragraph numbering exactly as it appears.';

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'manage_corpus', 'corpus_ocr_assist');
  if (capError) return capError;

  if (!GEMINI_API_KEY) {
    return json({ error: 'GEMINI_API_KEY is not configured for this project (Edge Function secret) — OCR assist cannot run until it is set' }, 502);
  }

  const body = await req.json().catch(() => null);
  const { file_base64, mime_type } = body ?? {};
  if (!file_base64 || !String(file_base64).trim()) return badRequest('file_base64 is required');
  if (!mime_type || (mime_type !== 'application/pdf' && !String(mime_type).startsWith('image/'))) {
    return badRequest('mime_type must be application/pdf or image/*');
  }
  if (String(file_base64).length > MAX_FILE_BASE64_CHARS) return badRequest('file is too large');

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
            parts: [
              { inline_data: { mime_type, data: file_base64 } },
              { text: 'Transcribe every page as JSON matching the response schema.' },
            ],
          }],
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

  let extraction: { pages: { page_number: number; text_content: string }[] };
  try {
    extraction = JSON.parse(textPart.text);
  } catch {
    return json({ error: 'model output was not valid JSON' }, 502);
  }

  const pages = extraction.pages || [];
  const text = pages.map((p) => p.text_content || '').join('\n\n');

  return json({ text, pages: pages.length });
});
