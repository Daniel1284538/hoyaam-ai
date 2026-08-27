// litigation-manage-parties
//
// POST { action: 'add',    matter_id, party_role, name, identifier?, notes? }
// POST { action: 'update', party_id, party_role?, name?, identifier?, notes? }
// POST { action: 'delete', party_id }
//
// legal-extract already auto-populates matter_parties from uploaded
// documents (high-confidence extractions apply automatically; low-
// confidence ones queue in `extractions` for litigation-review-extraction)
// — but there was previously no way to add a party by hand, fix a
// mis-extracted name, or remove a duplicate. This is that path: the
// Parties tab's own CRUD, independent of the extraction pipeline, for
// the same reason litigation-update-matter exists for the matter's own
// fields — automatic extraction is never the only way in, and it is
// never infallible.
//
// Gated on upload_documents, matching every other matter-intake-adjacent
// write in this build (litigation-create-matter's own inline party
// insert, litigation-update-matter) — the same HONEST GAP: no dedicated
// capability for party management exists in the permission matrix.
// update/delete additionally verify the party actually belongs to a
// matter the caller can access, fetched fresh rather than trusted from
// the client, so party_id alone is never enough to touch a row on a
// matter the caller can't see.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from './_shared/admin.ts';

const PARTY_ROLES = ['plaintiff', 'defendant', 'third_party', 'counsel_own_side', 'counsel_opposing'];

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'upload_documents', 'matter_party_managed');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  if (!body) return badRequest('invalid JSON body');
  const { action } = body;

  if (action === 'add') {
    const { matter_id, party_role, name, identifier, notes } = body;
    if (!matter_id) return badRequest('matter_id is required');
    if (!PARTY_ROLES.includes(party_role)) return badRequest(`party_role must be one of: ${PARTY_ROLES.join(', ')}`);
    if (!name || !String(name).trim()) return badRequest('name is required');

    const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
    if (!canAccess) return badRequest('no access to this matter');

    const { data: party, error } = await asService
      .from('matter_parties')
      .insert({ matter_id, party_role, name: String(name).trim(), identifier: identifier || null, notes: notes || null })
      .select('id')
      .single();
    if (error) return json({ error: error.message }, 409);

    await logAction(asService, callerId, 'matter_party_added', {
      targetTable: 'matter_parties', targetId: party.id,
      after: { matter_id, party_role, name: String(name).trim() },
    });
    return json({ party_id: party.id });
  }

  if (action === 'update' || action === 'delete') {
    const { party_id } = body;
    if (!party_id) return badRequest('party_id is required');

    const { data: existing, error: fetchError } = await asService
      .from('matter_parties')
      .select('id, matter_id, party_role, name, identifier, notes')
      .eq('id', party_id)
      .maybeSingle();
    if (fetchError) return json({ error: fetchError.message }, 500);
    if (!existing) return badRequest('no such party');

    const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: existing.matter_id });
    if (!canAccess) return badRequest('no access to this matter');

    if (action === 'delete') {
      const { error } = await asService.from('matter_parties').delete().eq('id', party_id);
      if (error) return json({ error: error.message }, 409);

      await logAction(asService, callerId, 'matter_party_removed', {
        targetTable: 'matter_parties', targetId: party_id,
        before: existing,
      });
      return json({ party_id, deleted: true });
    }

    // action === 'update'
    const { party_role, name, identifier, notes } = body;
    const patch: Record<string, string | null> = {};
    if ('party_role' in body) {
      if (!PARTY_ROLES.includes(party_role)) return badRequest(`party_role must be one of: ${PARTY_ROLES.join(', ')}`);
      patch.party_role = party_role;
    }
    if ('name' in body) {
      if (!name || !String(name).trim()) return badRequest('name must be a non-empty string');
      patch.name = String(name).trim();
    }
    if ('identifier' in body) patch.identifier = identifier || null;
    if ('notes' in body) patch.notes = notes || null;
    if (Object.keys(patch).length === 0) return badRequest('nothing to update — include at least one field');

    const { error } = await asService.from('matter_parties').update(patch).eq('id', party_id);
    if (error) return json({ error: error.message }, 409);

    await logAction(asService, callerId, 'matter_party_updated', {
      targetTable: 'matter_parties', targetId: party_id,
      before: existing, after: patch,
    });
    return json({ party_id });
  }

  return badRequest("action must be 'add', 'update', or 'delete'");
});
