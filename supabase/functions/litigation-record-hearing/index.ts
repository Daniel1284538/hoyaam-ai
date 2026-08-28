// litigation-record-hearing
//
// POST { matter_id, session_date, session_time?, outcome?, adjournment_reason?, next_session_date? }
//
// Plan section 01, Tier A: "reads the adjournment note, updates the next
// hearing date, and keeps one rolling roll across every matter." This is
// the manual-entry half of that — a human (clerk/lawyer) records what
// happened at the sitting. There is no OCR-of-the-adjournment-note path
// yet; that would be a legal-extract variant, not built here.
//
// HONEST GAP: same as litigation-create-matter — there's no dedicated
// capability for case data entry in the permission matrix, so this reuses
// upload_documents (held by clerk/lawyer/owner/trainee).

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const OUTCOMES = ['adjourned', 'reserved_for_judgment', 'judgment_issued', 'other'];

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'upload_documents', 'hearing_recorded');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { matter_id, session_date, session_time, outcome, adjournment_reason, next_session_date } = body ?? {};
  if (!matter_id || !session_date) return badRequest('matter_id and session_date are required');
  if (outcome !== undefined && outcome !== null && !OUTCOMES.includes(outcome)) {
    return badRequest(`outcome must be one of: ${OUTCOMES.join(', ')}`);
  }

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  const { data: hearing, error } = await asService
    .from('hearings')
    .insert({
      matter_id, session_date, session_time: session_time || null,
      outcome: outcome || null, adjournment_reason: adjournment_reason || null,
      next_session_date: next_session_date || null, recorded_by: callerId,
    })
    .select('id')
    .single();
  if (error) return json({ error: error.message }, 409);

  await logAction(asService, callerId, 'hearing_recorded', {
    targetTable: 'hearings', targetId: hearing.id,
    after: { matter_id, session_date, outcome, next_session_date },
  });

  return json({ hearing_id: hearing.id });
});
