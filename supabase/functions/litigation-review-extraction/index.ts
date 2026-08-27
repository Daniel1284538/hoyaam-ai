// litigation-review-extraction
//
// POST { extraction_id, action: 'confirm' | 'correct' | 'reject', corrected_value?, notes? }
//
// The human half of the confidence gate in legal-extract: every extraction
// that landed below EXTRACTION_THRESHOLD (or a party below it) sits here
// with review_status='pending' until a reviewer disposes of it. Unlike
// the auto-apply path, a human confirm/correct applies unconditionally —
// no "column must be empty" check — because a person is now the one
// deciding, not a confidence score.

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

  const capError = await requireCapability(asUser, asService, callerId, 'review_extractions', 'extraction_reviewed');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { extraction_id, action, corrected_value, notes } = body ?? {};
  if (!extraction_id || !['confirm', 'correct', 'reject'].includes(action)) {
    return badRequest("extraction_id and action ('confirm' | 'correct' | 'reject') are required");
  }
  if (action === 'correct' && (!corrected_value || !String(corrected_value).trim())) {
    return badRequest('corrected_value is required for action=correct');
  }

  const { data: extraction, error: exError } = await asService
    .from('extractions')
    .select('*')
    .eq('id', extraction_id)
    .maybeSingle();
  if (exError || !extraction) return badRequest('extraction not found');
  if (extraction.review_status !== 'pending') return badRequest(`already reviewed (status=${extraction.review_status})`);

  const finalValue = action === 'correct' ? String(corrected_value) : extraction.field_value;
  const newStatus = action === 'reject' ? 'rejected' : action === 'correct' ? 'corrected' : 'confirmed';

  const { error: updateError } = await asService
    .from('extractions')
    .update({
      review_status: newStatus,
      field_value: finalValue,
      reviewed_by: callerId,
      reviewed_at: new Date().toISOString(),
    })
    .eq('id', extraction_id);
  if (updateError) return json({ error: updateError.message }, 500);

  if (action !== 'reject') {
    if (extraction.field_key.startsWith('party:')) {
      const partyRole = extraction.field_key.slice('party:'.length);
      await asService.from('matter_parties').insert({
        matter_id: extraction.matter_id, party_role: partyRole, name: finalValue,
      });
    } else {
      const patch: Record<string, unknown> = {
        [extraction.field_key]: extraction.field_key === 'case_year' ? Number(finalValue) : finalValue,
      };
      await asService.from('matters').update(patch).eq('id', extraction.matter_id);
    }
  }

  await asService.from('review_actions').insert({
    subject_type: 'extraction',
    subject_id: extraction_id,
    matter_id: extraction.matter_id,
    action: newStatus,
    actor_id: callerId,
    notes: notes || null,
  });

  await logAction(asService, callerId, 'extraction_reviewed', {
    targetTable: 'extractions', targetId: extraction_id,
    after: { action: newStatus, field_key: extraction.field_key, value: finalValue },
  });

  return json({ extraction_id, status: newStatus });
});
