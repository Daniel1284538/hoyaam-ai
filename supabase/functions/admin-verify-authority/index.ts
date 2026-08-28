// admin-verify-authority
//
// POST { authority_id, decision: 'human_verified' | 'disputed', note? }
//
// The human sign-off step of the corpus trust ladder. Everything that
// enters the corpus starts at 'machine_ingested' — scraped or bulk-loaded
// text nobody has checked against the primary source. This is where a
// qualified person promotes it to 'human_verified', or flags it as
// 'disputed'.
//
// Why this is a separate endpoint rather than a field on the ingest call:
// the whole value of the status is that it means "someone other than the
// loader checked this". A pipeline that could assert its own output was
// verified would make the column decorative. So:
//   - the verifier must not be the person who added the authority
//     (also enforced by trg_authorities_no_self_verification, since the
//     Edge Function is not the only possible path to the table)
//   - 'disputed' requires a note — "this is wrong" is only actionable if
//     it says how
//   - nothing can go BACK to 'machine_ingested'. Once a human has looked,
//     that is a fact about the world and shouldn't be erasable by a later
//     write.
//
// HONEST GAP: this records that a human asserted verification. It cannot
// check that they actually did the work, and nothing here compares the
// stored text against an official source — no machine-readable
// authoritative feed of Egyptian legislation is wired up. The status is
// an accountability trail (who said so, when), not a proof.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const DECISIONS = ['human_verified', 'disputed'];

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'manage_corpus', 'authority_verified');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { authority_id, decision, note } = body ?? {};

  if (!authority_id) return badRequest('authority_id is required');
  if (!DECISIONS.includes(decision)) {
    return badRequest(`decision must be one of ${DECISIONS.join(', ')} — an authority cannot be returned to machine_ingested once a human has reviewed it`);
  }
  if (decision === 'disputed' && (!note || !String(note).trim())) {
    return badRequest('a note is required when marking an authority disputed — record what is wrong with it');
  }

  const { data: authority, error: fetchError } = await asService
    .from('authorities')
    .select('id, title, citation, added_by, verification_status, verified_by')
    .eq('id', authority_id)
    .maybeSingle();
  if (fetchError) return json({ error: fetchError.message }, 500);
  if (!authority) return badRequest('authority not found');

  // Mirrors trg_authorities_no_self_verification so the caller gets a clear
  // message rather than a raw trigger exception.
  if (authority.added_by && authority.added_by === callerId) {
    await logAction(asService, callerId, 'authority_verified', {
      targetTable: 'authorities', targetId: authority_id, success: false,
      reason: 'denied: cannot verify an authority you added yourself',
    });
    return json({ error: 'you cannot verify an authority you added yourself — verification must be independent of ingestion' }, 403);
  }

  const before = {
    verification_status: authority.verification_status,
    verified_by: authority.verified_by,
  };

  const { error: updateError } = await asService
    .from('authorities')
    .update({
      verification_status: decision,
      verified_by: callerId,
      verified_at: new Date().toISOString(),
      amendment_note: note ? String(note) : undefined,
    })
    .eq('id', authority_id);
  if (updateError) return json({ error: updateError.message }, 409);

  await logAction(asService, callerId, 'authority_verified', {
    targetTable: 'authorities',
    targetId: authority_id,
    before,
    after: { verification_status: decision, verified_by: callerId },
    reason: note ? String(note) : null,
  });

  return json({
    authority_id,
    title: authority.title,
    citation: authority.citation,
    verification_status: decision,
    verified_by: callerId,
  });
});
