// litigation-confirm-deadline
//
// POST { deadline_id }
//
// The human confirmation step every provisional deadline needs before a
// lawyer should rely on it (plan section 03). Only moves
// provisional -> confirmed; never re-confirms, never touches a deadline
// someone already disposed of.

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

  const capError = await requireCapability(asUser, asService, callerId, 'confirm_deadline', 'deadline_confirmed');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { deadline_id } = body ?? {};
  if (!deadline_id) return badRequest('deadline_id is required');

  const { data: deadline, error } = await asService
    .from('deadlines')
    .update({ status: 'confirmed', confirmed_by: callerId, confirmed_at: new Date().toISOString() })
    .eq('id', deadline_id)
    .eq('status', 'provisional')
    .select('matter_id')
    .single();
  if (error) return badRequest('no provisional deadline with that id');

  await asService.from('review_actions').insert({
    subject_type: 'deadline', subject_id: deadline_id, matter_id: deadline.matter_id,
    action: 'confirmed', actor_id: callerId,
  });

  await logAction(asService, callerId, 'deadline_confirmed', {
    targetTable: 'deadlines', targetId: deadline_id, after: { status: 'confirmed' },
  });

  return json({ deadline_id, status: 'confirmed' });
});
