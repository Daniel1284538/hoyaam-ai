// litigation-update-matter
//
// POST { matter_id, matter_label?, court?, circuit?, case_number?, case_year?, matter_type?, stage?, subject? }
//
// litigation-create-matter now requires matter_label/court/circuit/
// case_number/case_year/matter_type/stage/subject up front — but a lot of
// matters already exist from before that requirement, and even a careful
// intake can miss one field in the moment (this is exactly what prompted
// this function: a matter created with no subject). This is the fix-up
// path: any subset of those fields, added or corrected after the fact,
// without having to know or re-enter everything else.
//
// Only keys actually present in the body are touched. A present key set to
// a real value updates that field; a present key set to null explicitly
// clears it. Same per-field validation as litigation-create-matter (an
// empty string is never accepted as "clearing" — send null for that,
// exactly the same distinction create draws with its required fields).
//
// Gated on upload_documents, same HONEST GAP as litigation-create-matter
// (no dedicated capability for matter management exists in the permission
// matrix), AND can_access_matter — holding the capability is not enough on
// its own; the caller also has to actually have access to THIS matter
// (blocked by an ethical wall, for instance, even if they hold the
// capability generally).

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from './_shared/admin.ts';

const STAGES = ['first_instance', 'appeal', 'cassation', 'execution'];
const TEXT_FIELDS = ['matter_label', 'court', 'circuit', 'case_number', 'matter_type', 'subject'] as const;

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'upload_documents', 'matter_updated');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  if (!body) return badRequest('invalid JSON body');

  const { matter_id } = body;
  if (!matter_id) return badRequest('matter_id is required');

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  const { data: before, error: beforeError } = await asService
    .from('matters')
    .select('matter_label, court, circuit, case_number, case_year, matter_type, stage, subject')
    .eq('id', matter_id)
    .single();
  if (beforeError || !before) return badRequest('no such matter');

  const patch: Record<string, string | number | null> = {};

  for (const key of TEXT_FIELDS) {
    if (!(key in body)) continue;
    const v = body[key];
    if (v === null) { patch[key] = null; continue; }
    if (typeof v !== 'string' || !v.trim()) {
      return badRequest(`${key} must be a non-empty string, or null to clear it`);
    }
    patch[key] = v.trim();
  }

  if ('stage' in body) {
    const v = body.stage;
    if (v !== null && !STAGES.includes(v)) {
      return badRequest(`stage must be null or one of: ${STAGES.join(', ')}`);
    }
    patch.stage = v;
  }

  if ('case_year' in body) {
    const v = body.case_year;
    if (v !== null && !Number.isInteger(v)) {
      return badRequest('case_year must be an integer, or null to clear it');
    }
    patch.case_year = v;
  }

  if (Object.keys(patch).length === 0) {
    return badRequest('nothing to update — include at least one field');
  }

  const { data: updated, error } = await asService
    .from('matters')
    .update(patch)
    .eq('id', matter_id)
    .select('id')
    .single();
  if (error) return json({ error: error.message }, 409);

  await logAction(asService, callerId, 'matter_updated', {
    targetTable: 'matters',
    targetId: matter_id,
    before,
    after: patch,
  });

  return json({ matter_id: updated.id });
});
