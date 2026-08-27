// litigation-create-matter
//
// POST { matter_label, court, circuit, case_number, case_year, matter_type, stage, subject | subject_pending_documents: true, parties?: [{ party_role, name, identifier?, notes? }] }
//
// Manual intake. This is a stand-in for Tier A's real capability ("Case
// intake from scans" — plan section 01): photograph/upload the file and
// have a structured record come back via the legal-ingest/legal-extract
// pipeline. That pipeline exists now too (see legal-ingest/legal-extract),
// but this function still matters for matters that don't start from a
// scanned file, gated behind the same upload_documents capability the
// real intake path uses.
//
// HONEST GAP: there is no dedicated "create_matter" capability in the
// permission matrix (see capabilities table) — upload_documents is reused
// here as the closest fit. If matter creation should be governed
// separately from document upload, that's a product decision for Phase 0,
// not something this function should decide unilaterally.
//
// Every one of these fields is required except parties — a real matter
// missing court/case_number/subject/etc. is created every bit as silently
// wrong as one missing matter_label, and this schema had no way to tell
// "not entered yet" apart from "genuinely doesn't exist for this matter."
// A matter created before this requirement (or missing a field for any
// other reason) is fixed up with litigation-update-matter afterward — this
// function only governs what a NEW matter needs at intake, it does not
// retroactively require anything of existing rows.
//
// subject is the one exception with an explicit escape hatch:
// subject_pending_documents=true skips the "subject is required" check
// and leaves the column genuinely NULL, for the case where the caller is
// about to upload the case's own scanned documents right after creating
// the matter, rather than type a subject by hand. This is NOT a way to
// bypass the requirement generally — it exists specifically so the
// frontend can offer "type it" or "upload documents instead" as two real
// options rather than forcing a placeholder subject into a column that's
// supposed to hold something true. It stays NULL, never a fabricated
// placeholder string, so legal-extract's own "only auto-apply into an
// empty column" gate still recognizes the field as unset and fills it
// from a high-confidence extraction the moment one exists.
//
// The creator gets a matter_access row on their own matter, granted to
// themselves, right here. can_access_matter() already gives owner/admin/
// lawyer blanket firm-wide visibility (access_01), so this is redundant
// for those three — but upload_documents (and so matter creation) is also
// held by trainee and clerk, neither of which has blanket visibility.
// Without this grant, a trainee or clerk who creates a matter would create
// it and then immediately be unable to see it again. A normal self-grant
// would be rejected by trg_matter_access_no_self_grant (user_id =
// granted_by is blocked by design, to stop self-service privilege
// escalation onto matters someone else created) — access_02_creator_
// self_grant_exception.sql carves out exactly this one case: the creator
// of THIS matter, exactly once, and never again after an explicit
// revocation. See that migration for why the carve-out stays narrow.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const STAGES = ['first_instance', 'appeal', 'cassation', 'execution'];
const PARTY_ROLES = ['plaintiff', 'defendant', 'third_party', 'counsel_own_side', 'counsel_opposing'];

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'upload_documents', 'matter_created');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  if (!body) return badRequest('invalid JSON body');

  const { matter_label, court, circuit, case_number, case_year, matter_type, stage, subject, subject_pending_documents, parties } = body;

  const REQUIRED_TEXT_FIELDS: [string, unknown][] = [
    ['matter_label', matter_label], ['court', court], ['circuit', circuit],
    ['case_number', case_number], ['matter_type', matter_type],
    ...(subject_pending_documents === true ? [] : [['subject', subject] as [string, unknown]]),
  ];
  for (const [name, value] of REQUIRED_TEXT_FIELDS) {
    if (!value || !String(value).trim()) return badRequest(`${name} is required`);
  }
  if (subject_pending_documents === true && subject != null && String(subject).trim()) {
    return badRequest('subject_pending_documents cannot be combined with a non-empty subject — pick one');
  }
  if (!stage || !STAGES.includes(stage)) {
    return badRequest(`stage is required and must be one of: ${STAGES.join(', ')}`);
  }
  if (!Number.isInteger(case_year)) {
    return badRequest('case_year is required and must be an integer');
  }
  if (parties !== undefined && parties !== null) {
    if (!Array.isArray(parties)) return badRequest('parties must be an array');
    for (const p of parties) {
      if (!p || !PARTY_ROLES.includes(p.party_role) || !p.name || !String(p.name).trim()) {
        return badRequest(`each party needs a name and a party_role in: ${PARTY_ROLES.join(', ')}`);
      }
    }
  }

  const { data: matter, error } = await asService
    .from('matters')
    .insert({
      matter_label,
      court: court || null,
      circuit: circuit || null,
      case_number: case_number || null,
      case_year: case_year ?? null,
      matter_type: matter_type || null,
      stage: stage || null,
      subject: subject || null,
      created_by: callerId,
    })
    .select('id')
    .single();
  if (error) return json({ error: error.message }, 409);

  // Report failures past this point rather than silently dropping them —
  // the matter row already exists, so there is nothing left to roll back.
  const partialFailures: string[] = [];

  const { data: grant, error: accessError } = await asService
    .from('matter_access')
    .insert({
      matter_id: matter.id,
      user_id: callerId,
      granted_by: callerId,
      reason: 'matter creator — automatic access on intake',
    })
    .select('id')
    .single();
  if (accessError) {
    partialFailures.push(`matter created but the creator's own access grant failed: ${accessError.message}`);
  } else {
    await logAction(asService, callerId, 'matter_access_granted', {
      targetTable: 'matter_access',
      targetId: grant.id,
      after: { matter_id: matter.id, user_id: callerId, reason: 'matter creator — automatic access on intake' },
    });
  }

  if (Array.isArray(parties) && parties.length > 0) {
    const rows = parties.map((p: { party_role: string; name: string; identifier?: string; notes?: string }) => ({
      matter_id: matter.id,
      party_role: p.party_role,
      name: p.name,
      identifier: p.identifier || null,
      notes: p.notes || null,
    }));
    const { error: partyError } = await asService.from('matter_parties').insert(rows);
    if (partyError) {
      partialFailures.push(`matter created but parties failed: ${partyError.message}`);
    }
  }

  await logAction(asService, callerId, 'matter_created', {
    targetTable: 'matters',
    targetId: matter.id,
    after: { matter_label, court, case_number, case_year, stage },
  });

  if (partialFailures.length > 0) {
    return json({ matter_id: matter.id, error: partialFailures.join('; ') }, 207);
  }
  return json({ matter_id: matter.id });
});
