// litigation-propose-deadline
//
// POST { matter_id, rule_id, trigger_event, trigger_date }
//
// The ONLY way a deadlines row gets created. Computes computed_due_date
// via fn_compute_deadline (deterministic, plan section 03) and always
// inserts as status='provisional' — nothing here is trusted until
// litigation-confirm-deadline is called by a human. fn_compute_deadline
// itself refuses to run against anything but a status='active' rule, so
// this cannot produce a real deadline before Phase 0's rules are signed.
//
// Gated on confirm_deadline (not a lesser capability) even though this is
// only the "propose" half — deadlines are the highest-consequence numbers
// in the system (plan section 03), so lawyer/owner-only from the moment
// one is even proposed, not just at confirmation.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'confirm_deadline', 'deadline_proposed');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { matter_id, rule_id, trigger_event, trigger_date } = body ?? {};
  if (!matter_id || !rule_id || !trigger_event || !trigger_date) {
    return badRequest('matter_id, rule_id, trigger_event and trigger_date are required');
  }

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  const { data: dueDate, error: computeError } = await asService.rpc('fn_compute_deadline', {
    p_trigger_date: trigger_date, p_rule_id: rule_id,
  });
  if (computeError) return badRequest(computeError.message);

  const { data: deadline, error } = await asService
    .from('deadlines')
    .insert({ matter_id, rule_id, trigger_event, trigger_date, computed_due_date: dueDate, status: 'provisional' })
    .select('id')
    .single();
  if (error) return json({ error: error.message }, 409);

  await logAction(asService, callerId, 'deadline_proposed', {
    targetTable: 'deadlines', targetId: deadline.id,
    after: { matter_id, rule_id, trigger_event, trigger_date, computed_due_date: dueDate },
  });

  return json({ deadline_id: deadline.id, computed_due_date: dueDate, status: 'provisional' });
});
