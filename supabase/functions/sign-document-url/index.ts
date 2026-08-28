// sign-document-url
//
// POST { matter_id, bucket, storage_path }
//
// The ONLY path to a stored file. No storage.objects RLS policy exists for
// authenticated on any bucket (see the storage migration), so a client-side
// download/list/signed-URL call always fails — this function, running as
// service_role, is the sole way in. It writes document_access_log before
// returning anything, on both the granted and the denied path, because a
// denied attempt is exactly as interesting as a successful one when
// somebody later asks "who tried to open this file".
//
// authorities (the shared legal corpus) isn't matter-scoped the way client
// documents are — matter_id is optional for that bucket and access is
// gated on run_research/generate_draft instead of can_access_matter.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, serviceClient, json, badRequest, AdminError, corsPreflight } from '../_shared/admin.ts';

const KNOWN_BUCKETS = new Set(['matter-documents', 'matter-drafts', 'authorities']);
const URL_TTL_SECONDS = 300;

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const body = await req.json().catch(() => null);
  const { matter_id, bucket, storage_path } = body ?? {};
  if (!bucket || !storage_path) return badRequest('bucket and storage_path are required');
  if (!KNOWN_BUCKETS.has(bucket)) return badRequest(`unknown bucket: ${bucket}`);
  if (bucket !== 'authorities' && !matter_id) {
    return badRequest('matter_id is required for matter-documents and matter-drafts');
  }

  let allowed = bucket === 'authorities';
  if (!allowed) {
    const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
    allowed = !!canAccess;
  }

  if (!allowed) {
    await asService.from('document_access_log').insert({
      matter_id: matter_id ?? null,
      bucket_id: bucket,
      storage_path,
      user_id: callerId,
      action: 'denied',
    });
    return new AdminError(403, 'no access to this matter');
  }

  const { data: signed, error } = await asService.storage
    .from(bucket)
    .createSignedUrl(storage_path, URL_TTL_SECONDS);

  if (error || !signed?.signedUrl) {
    await asService.from('document_access_log').insert({
      matter_id: matter_id ?? null,
      bucket_id: bucket,
      storage_path,
      user_id: callerId,
      action: 'denied',
      reason: error?.message ?? 'signing failed',
    });
    return json({ error: error?.message ?? 'signing failed' }, 500);
  }

  await asService.from('document_access_log').insert({
    matter_id: matter_id ?? null,
    bucket_id: bucket,
    storage_path,
    user_id: callerId,
    action: 'view',
  });

  return json({ url: signed.signedUrl, expires_in: URL_TTL_SECONDS });
});
