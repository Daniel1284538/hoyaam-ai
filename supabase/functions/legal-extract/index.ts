// legal-extract
//
// POST { document_id, job_id? }
//
// Step 2 of the intake pipeline. Downloads the uploaded file (service
// role — no client ever touches Storage directly, see legal-ingest),
// sends it to Gemini in one call for BOTH raw per-page transcription
// (-> document_pages / document_chunks, feeds archive search) and
// structured field extraction (-> extractions, feeds matters/
// matter_parties), forced through responseSchema so the output is
// fields, not prose (plan section 03, "Arabic on paper, not Arabic on
// screen").
//
// Provider: Gemini (gemini-3.6-flash by default), not Claude — switched
// on request. Uses generateContent with responseMimeType=application/json
// + responseSchema for forced structured output, and inline_data for the
// file (base64 PDF or image — no separate Files API upload needed at
// this size).
//
// Confidence gate: a field is auto-applied to matters/matter_parties ONLY
// when confidence >= EXTRACTION_THRESHOLD *and* the target column is
// still empty — extraction fills gaps, it never overwrites something a
// human already entered. Anything below the threshold lands in
// `extractions` with review_status='pending' for a human to confirm,
// correct, or reject via litigation-review-extraction. Nothing here
// writes to `matters`/`matter_parties` without going through this gate.
//
// REQUIRES the GEMINI_API_KEY Edge Function secret. Fails loudly (not
// silently) if it isn't set — marks the document/job failed rather than
// hanging or pretending to succeed.
//
// Embeddings: document_chunks.embedding is left NULL here. No embeddings
// provider key exists in this project yet (plan cost table: "Embeddings:
// separate provider"). Arabic full-text search over chunk_text already
// works without it; the vector half of hybrid search activates once that
// key is added and a backfill embeds existing chunks — see
// litigation-search-archive.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';
const EXTRACTION_THRESHOLD = 0.85;
const STAGES = ['first_instance', 'appeal', 'cassation', 'execution'];
const PARTY_ROLES = ['plaintiff', 'defendant', 'third_party', 'counsel_own_side', 'counsel_opposing'];

function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
  }
  return btoa(binary);
}

const FIELD_SCHEMA = {
  type: 'OBJECT',
  properties: { value: { type: 'STRING', nullable: true }, confidence: { type: 'NUMBER' } },
  required: ['confidence'],
};

const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  required: ['pages', 'fields', 'parties'],
  properties: {
    pages: {
      type: 'ARRAY',
      description: 'One entry per page in the document, in order, starting at 1.',
      items: {
        type: 'OBJECT',
        required: ['page_number', 'text_content'],
        properties: {
          page_number: { type: 'INTEGER' },
          text_content: { type: 'STRING', description: 'Full transcribed text of this page, in Arabic as written.' },
        },
      },
    },
    fields: {
      type: 'OBJECT',
      properties: {
        court: FIELD_SCHEMA,
        circuit: FIELD_SCHEMA,
        case_number: FIELD_SCHEMA,
        case_year: FIELD_SCHEMA,
        matter_type: FIELD_SCHEMA,
        stage: {
          type: 'OBJECT',
          properties: { value: { type: 'STRING', nullable: true, enum: STAGES }, confidence: { type: 'NUMBER' } },
          required: ['confidence'],
        },
        subject: FIELD_SCHEMA,
      },
    },
    parties: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        required: ['party_role', 'name', 'confidence'],
        properties: {
          party_role: { type: 'STRING', enum: PARTY_ROLES },
          name: { type: 'STRING' },
          identifier: { type: 'STRING', nullable: true },
          confidence: { type: 'NUMBER' },
        },
      },
    },
  },
};

const SYSTEM_INSTRUCTION =
  'You transcribe and extract structured fields from Egyptian litigation case files (scanned, often stamped, handwritten margin notes). Transcribe exactly as written — do not translate or normalize Arabic text. Extract only what is actually present; use a LOW confidence (below 0.85) rather than guessing whenever the source is unclear, handwritten, ambiguous, or you are inferring — never inflate confidence to avoid a review flag.';

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const body = await req.json().catch(() => null);
  const { document_id, job_id } = body ?? {};
  if (!document_id) return badRequest('document_id is required');

  const { data: doc, error: docError } = await asService
    .from('documents')
    .select('id, matter_id, bucket_id, storage_path, mime_type, original_filename')
    .eq('id', document_id)
    .maybeSingle();
  if (docError || !doc) return badRequest('document not found');

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: doc.matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  const capError = await requireCapability(asUser, asService, callerId, 'upload_documents', 'document_extract_requested');
  if (capError) return capError;

  async function fail(reason: string) {
    await asService.from('documents').update({ ocr_status: 'failed' }).eq('id', document_id);
    if (job_id) {
      await asService.from('ingestion_jobs').update({ status: 'failed', last_error: reason }).eq('id', job_id);
      await asService.from('job_events').insert({ job_id, event: 'extraction_failed', detail: { reason } });
    }
    return json({ error: reason }, 502);
  }

  if (!GEMINI_API_KEY) {
    return await fail('GEMINI_API_KEY is not configured for this project (Edge Function secret) — extraction cannot run until it is set');
  }

  if (doc.mime_type !== 'application/pdf' && !String(doc.mime_type || '').startsWith('image/')) {
    return badRequest('unsupported mime_type for extraction — only application/pdf or image/* are supported');
  }

  await asService.from('documents').update({ ocr_status: 'processing' }).eq('id', document_id);
  if (job_id) {
    await asService.from('ingestion_jobs').update({ status: 'processing' }).eq('id', job_id);
    await asService.from('job_events').insert({ job_id, event: 'extraction_started', detail: { document_id } });
  }

  const { data: fileBlob, error: dlError } = await asService.storage.from(doc.bucket_id).download(doc.storage_path);
  if (dlError || !fileBlob) return await fail(`could not download file: ${dlError?.message ?? 'unknown error'}`);

  const bytes = new Uint8Array(await fileBlob.arrayBuffer());
  const base64 = bytesToBase64(bytes);

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
              { inline_data: { mime_type: doc.mime_type, data: base64 } },
              { text: 'Transcribe every page and extract the case-file fields and parties as JSON matching the response schema.' },
            ],
          }],
          generationConfig: {
            temperature: 0,
            responseMimeType: 'application/json',
            responseSchema: RESPONSE_SCHEMA,
          },
        }),
      },
    );
  } catch (e) {
    return await fail(`Gemini API request failed: ${e instanceof Error ? e.message : String(e)}`);
  }

  if (!apiResp.ok) {
    const errText = await apiResp.text().catch(() => '');
    return await fail(`Gemini API returned ${apiResp.status}: ${errText.slice(0, 500)}`);
  }

  const apiJson = await apiResp.json();
  const blockReason = apiJson.promptFeedback?.blockReason;
  if (blockReason) return await fail(`Gemini blocked the request: ${blockReason}`);

  const textPart = apiJson.candidates?.[0]?.content?.parts?.find((p: { text?: string }) => typeof p.text === 'string');
  if (!textPart) return await fail('model did not return the expected JSON output');

  let extraction: {
    pages: { page_number: number; text_content: string }[];
    fields: Record<string, { value: string | null; confidence: number }>;
    parties: { party_role: string; name: string; identifier: string | null; confidence: number }[];
  };
  try {
    extraction = JSON.parse(textPart.text);
  } catch {
    return await fail('model output was not valid JSON');
  }

  // Pages -> document_pages + one chunk per page (embedding left null, see header note).
  for (const p of extraction.pages || []) {
    const { data: pageRow, error: pageErr } = await asService
      .from('document_pages')
      .upsert({ document_id, page_number: p.page_number, text_content: p.text_content }, { onConflict: 'document_id,page_number' })
      .select('id')
      .single();
    if (pageErr) continue;
    await asService.from('document_chunks').insert({
      document_id,
      page_id: pageRow.id,
      chunk_text: p.text_content,
    });
  }

  // Fields -> extractions, and auto-apply to matters only where confidence
  // clears the bar AND the matter's own column is still empty.
  const { data: matterRow } = await asService
    .from('matters')
    .select('court, circuit, case_number, case_year, matter_type, stage, subject')
    .eq('id', doc.matter_id)
    .single();

  const patch: Record<string, unknown> = {};
  const applied: string[] = [];
  const pendingReview: string[] = [];

  for (const [key, field] of Object.entries(extraction.fields || {})) {
    if (field.value === null || field.value === undefined || field.value === '') continue;
    const highConfidence = field.confidence >= EXTRACTION_THRESHOLD;
    const columnEmpty = matterRow && (matterRow as Record<string, unknown>)[key] == null;
    const willApply = highConfidence && columnEmpty;

    await asService.from('extractions').insert({
      document_id,
      matter_id: doc.matter_id,
      field_key: key,
      field_value: String(field.value),
      confidence: field.confidence,
      review_status: willApply ? 'confirmed' : 'pending',
    });

    if (willApply) {
      patch[key] = key === 'case_year' ? Number(field.value) : field.value;
      applied.push(key);
    } else {
      pendingReview.push(key);
    }
  }
  if (Object.keys(patch).length > 0) {
    await asService.from('matters').update(patch).eq('id', doc.matter_id);
  }

  let partiesApplied = 0;
  for (const p of extraction.parties || []) {
    if (!p.name || !PARTY_ROLES.includes(p.party_role)) continue;
    if (p.confidence >= EXTRACTION_THRESHOLD) {
      await asService.from('matter_parties').insert({
        matter_id: doc.matter_id, party_role: p.party_role, name: p.name, identifier: p.identifier || null,
      });
      partiesApplied++;
    } else {
      await asService.from('extractions').insert({
        document_id, matter_id: doc.matter_id, field_key: `party:${p.party_role}`, field_value: p.name,
        confidence: p.confidence, review_status: 'pending',
      });
      pendingReview.push(`party:${p.party_role}`);
    }
  }

  await asService.from('documents').update({ ocr_status: 'done', page_count: (extraction.pages || []).length }).eq('id', document_id);
  if (job_id) {
    await asService.from('ingestion_jobs').update({ status: 'done' }).eq('id', job_id);
    await asService.from('job_events').insert({ job_id, event: 'extraction_completed', detail: { applied, pendingReview } });
  }

  await logAction(asService, callerId, 'document_extracted', {
    targetTable: 'documents', targetId: document_id,
    after: { fields_applied: applied, fields_pending_review: pendingReview, parties_applied: partiesApplied },
  });

  return json({
    document_id,
    pages: (extraction.pages || []).length,
    fields_applied: applied,
    fields_pending_review: pendingReview,
    parties_applied: partiesApplied,
  });
});
