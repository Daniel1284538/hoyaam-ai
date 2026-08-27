// legal-ingest
//
// POST { matter_id, original_filename, mime_type }
// -> { document_id, storage_path, upload_url, token }
//
// Step 1 of the intake pipeline (plan section 01, "Case intake from
// scans"). Creates the documents + ingestion_jobs rows and hands back a
// signed upload URL — the client then PUTs the file bytes directly to
// Storage (supabase-js uploadToSignedUrl), and calls legal-extract with
// the returned document_id once that upload finishes. This function never
// touches file bytes itself.
//
// storage.objects has no policies for `authenticated` on any bucket (see
// the original storage migration) — a signed upload URL, minted here
// under service_role after a capability + matter_access check, is the
// only way a client can write into matter-documents at all.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const BUCKET = 'matter-documents';

function sanitizeFilename(name: string): string {
  return String(name || 'file').replace(/[^\w.\-؀-ۿ]+/g, '_').slice(0, 120);
}

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'upload_documents', 'document_ingest_requested');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { matter_id, original_filename, mime_type } = body ?? {};
  if (!matter_id) return badRequest('matter_id is required');

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  const documentId = crypto.randomUUID();
  const storagePath = `${matter_id}/${documentId}-${sanitizeFilename(original_filename)}`;

  const { error: docError } = await asService.from('documents').insert({
    id: documentId,
    matter_id,
    bucket_id: BUCKET,
    storage_path: storagePath,
    original_filename: original_filename || null,
    mime_type: mime_type || null,
    ocr_status: 'pending',
    uploaded_by: callerId,
  });
  if (docError) return json({ error: docError.message }, 409);

  const { data: job, error: jobError } = await asService
    .from('ingestion_jobs')
    .insert({ matter_id, status: 'queued' })
    .select('id')
    .single();
  if (jobError) return json({ error: jobError.message }, 500);

  await asService.from('job_events').insert({
    job_id: job.id,
    event: 'document_registered',
    detail: { document_id: documentId, original_filename },
  });

  const { data: signed, error: signError } = await asService.storage
    .from(BUCKET)
    .createSignedUploadUrl(storagePath);
  if (signError || !signed) {
    return json({ error: signError?.message ?? 'failed to create upload URL' }, 500);
  }

  await logAction(asService, callerId, 'document_ingest_requested', {
    targetTable: 'documents',
    targetId: documentId,
    after: { matter_id, original_filename },
  });

  return json({
    document_id: documentId,
    job_id: job.id,
    storage_path: storagePath,
    upload_url: signed.signedUrl,
    token: signed.token,
  });
});
