// litigation-verify-citation
//
// POST { citation_id, status: 'verified' | 'flagged' } -> the updated row
//
// The write side of the Citation & Provision Inspector. A lawyer looks at
// a draft_citations row next to the verbatim authority_chunk text it's
// bound to and decides whether the draft's claim actually matches what
// the source says. litigation-export-draft already blocks export while
// any citation is 'unverified' or 'flagged' — this is the only path that
// resolves one to 'verified' and lets export proceed.
//
// Deliberately narrower than it could be:
//   - 'verified' is refused if authority_chunk_id is null. An unbound
//     citation (free-text, no retrieved passage behind it) has nothing to
//     check it against — marking it "verified" would just be trusting the
//     draft's own word, which is exactly what this screen exists to not
//     do. It can still be flagged.
//   - Only 'verified' and 'flagged' are accepted — nothing can be pushed
//     back to 'unverified' through this endpoint; that's the state new
//     citations start in, not a status a human actively chooses.
//   - The caller must have matter access to the draft this citation
//     belongs to, same as any other matter-scoped write in this build.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const STATUSES = ['verified', 'flagged'];

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'export_matter', 'citation_reviewed');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { citation_id, status } = body ?? {};
  if (!citation_id) return badRequest('citation_id is required');
  if (!STATUSES.includes(status)) return badRequest(`status must be one of ${STATUSES.join(', ')}`);

  const { data: citation, error: fetchError } = await asService
    .from('draft_citations')
    .select('id, citation_text, status, authority_chunk_id, draft_id, drafts(matter_id)')
    .eq('id', citation_id)
    .maybeSingle();
  if (fetchError) return json({ error: fetchError.message }, 500);
  if (!citation) return badRequest('citation not found');

  const matterId = (citation as any).drafts?.matter_id;
  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matterId });
  if (!canAccess) return badRequest('no access to this matter');

  if (status === 'verified' && !citation.authority_chunk_id) {
    return badRequest('cannot verify a citation that is not bound to a retrieved passage — there is nothing to check it against, mark it flagged instead');
  }

  const before = { status: citation.status };
  const { error: updateError } = await asService
    .from('draft_citations')
    .update({ status })
    .eq('id', citation_id);
  if (updateError) return json({ error: updateError.message }, 409);

  await logAction(asService, callerId, 'citation_reviewed', {
    targetTable: 'draft_citations', targetId: citation_id,
    before, after: { status },
  });

  await asService.from('review_actions').insert({
    subject_type: 'draft_citation', subject_id: citation_id, matter_id: matterId,
    action: status, actor_id: callerId,
  });

  return json({ citation_id, status });
});
