// litigation-fill-template
//
// POST { matter_id, template_id, overrides? } -> { draft_id, version, content_text, variables }
//
// The deterministic, non-AI drafting fast path — a complement to
// litigation-draft/litigation-memo, not a replacement. Those two ask
// Gemini to write prose; this one does plain {{variable}} substitution
// into a template's content_text using this matter's actual stored
// fields, with zero model involvement. No legal reasoning happens here,
// so there is nothing to fabricate and nothing to cite — it exists for
// the "just fill in the case number and party names on our standard
// form" case, which is most of what a firm actually files day to day.
//
// Every variable starts from a real column (matters / matter_parties /
// hearings / the caller's own profile) or is left as an explicit,
// visible placeholder — never a guessed or invented value. Fields with
// no schema backing at all (claim amount, opponent's address, court
// building address — none of these exist as columns anywhere in this
// project) are ALWAYS placeholders unless the caller supplies them via
// `overrides`; this function will never invent a number or an address.
//
// The client renders its own live preview locally as the lawyer types
// (pure string substitution, no network round trip per keystroke) using
// the same variable set — but what gets PERSISTED here is computed
// server-side from a freshly-fetched template.content_text and the
// caller's final variables, never from client-supplied rendered text.
// That keeps a saved draft provably "this template + these variables",
// not arbitrary text a client happened to send.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const STAGE_LABELS: Record<string, string> = {
  first_instance: 'ابتدائي', appeal: 'استئناف', cassation: 'نقض', execution: 'تنفيذ',
};

const MISSING = '[يُستكمل يدوياً]';

// The full set of {{tokens}} this engine understands. Anything else in
// overrides is dropped rather than silently smuggled into the document.
const VAR_KEYS = [
  'matter_label', 'court', 'circuit', 'case_number', 'case_year', 'stage', 'subject',
  'plaintiff_name', 'defendant_name', 'next_hearing_date', 'lawyer_name', 'date',
  'claim_amount', 'opponent_address', 'court_address',
];

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'generate_draft', 'template_filled');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { matter_id, template_id, overrides } = body ?? {};
  if (!matter_id || !template_id) return badRequest('matter_id and template_id are required');
  if (overrides != null && (typeof overrides !== 'object' || Array.isArray(overrides))) {
    return badRequest('overrides must be an object of { variable_key: string }');
  }

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  const [{ data: template, error: templateError }, { data: matter }, { data: parties }, { data: nextHearing }, { data: profile }] = await Promise.all([
    asService.from('templates').select('id, title, doc_type, content_text').eq('id', template_id).maybeSingle(),
    asService.from('matters').select('matter_label, court, circuit, case_number, case_year, stage, subject').eq('id', matter_id).maybeSingle(),
    asService.from('matter_parties').select('party_role, name').eq('matter_id', matter_id).in('party_role', ['plaintiff', 'defendant']).order('created_at', { ascending: true }),
    asService.from('hearings').select('session_date').eq('matter_id', matter_id).gte('session_date', new Date().toISOString().slice(0, 10)).order('session_date', { ascending: true }).limit(1).maybeSingle(),
    asService.from('profiles').select('full_name').eq('id', callerId).maybeSingle(),
  ]);
  if (templateError) return json({ error: templateError.message }, 500);
  if (!template) return badRequest('template not found');
  if (!matter) return badRequest('matter not found');

  const plaintiff = (parties || []).find((p) => p.party_role === 'plaintiff');
  const defendant = (parties || []).find((p) => p.party_role === 'defendant');

  // Base variables — every one traces to a real row, or is left empty
  // (empty means MISSING at render time, never a guess).
  const base: Record<string, string> = {
    matter_label: matter.matter_label || '',
    court: matter.court || '',
    circuit: matter.circuit || '',
    case_number: matter.case_number || '',
    case_year: matter.case_year != null ? String(matter.case_year) : '',
    stage: matter.stage ? (STAGE_LABELS[matter.stage] || matter.stage) : '',
    subject: matter.subject || '',
    plaintiff_name: plaintiff?.name || '',
    defendant_name: defendant?.name || '',
    next_hearing_date: nextHearing?.session_date || '',
    lawyer_name: profile?.full_name || '',
    date: new Date().toISOString().slice(0, 10),
    // No schema column anywhere backs these three — always a placeholder
    // unless the caller explicitly typed one in via overrides.
    claim_amount: '',
    opponent_address: '',
    court_address: '',
  };

  const finalVars: Record<string, string> = { ...base };
  if (overrides) {
    for (const key of VAR_KEYS) {
      const v = (overrides as Record<string, unknown>)[key];
      if (typeof v === 'string' && v.trim()) finalVars[key] = v.trim();
    }
  }

  let rendered = String(template.content_text || '');
  for (const key of VAR_KEYS) {
    const value = finalVars[key] && finalVars[key].trim() ? finalVars[key] : MISSING;
    rendered = rendered.replaceAll(`{{${key}}}`, value);
  }

  const { data: existing } = await asService
    .from('drafts')
    .select('version')
    .eq('matter_id', matter_id)
    .eq('doc_type', template.doc_type)
    .order('version', { ascending: false })
    .limit(1)
    .maybeSingle();
  const version = (existing?.version ?? 0) + 1;

  const { data: draft, error: draftError } = await asService
    .from('drafts')
    .insert({ matter_id, doc_type: template.doc_type, template_id, version, content_text: rendered, status: 'ready_for_review', created_by: callerId })
    .select('id')
    .single();
  if (draftError) return json({ error: draftError.message }, 500);

  await logAction(asService, callerId, 'template_filled', {
    targetTable: 'drafts', targetId: draft.id,
    after: { matter_id, template_id, doc_type: template.doc_type, version },
  });

  return json({ draft_id: draft.id, version, content_text: rendered, variables: finalVars });
});
