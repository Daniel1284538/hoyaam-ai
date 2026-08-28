// litigation-check-conflicts
//
// POST { names: string[], matter_id? } -> { matches, overall_recommendation, ai_notes, ai_error? }
//
// A conflict-of-interest check has to search PARTY NAMES across the
// ENTIRE firm, not just matters the calling lawyer has matter_access to
// -- that is the whole point: it has to catch a conflict in a matter the
// checking lawyer has never seen and may never be granted access to.
// fn_check_conflicts (conflicts_01_check_rpc) is therefore SECURITY
// DEFINER and deliberately crosses matter_access/ethical-wall
// boundaries -- but it returns ONLY matter_id, matter_label, party_role,
// name, and identifier, never case content. This function is the only
// sanctioned way to reach it: gated on upload_documents (the same
// HONEST-GAP capability litigation-create-matter itself uses, since a
// conflict check happens at/around matter intake and there is no
// dedicated capability for either), and every call is logged.
//
// AI's role here is advisory triage only, over the raw matches --
// never detection. The raw `matches` array (from fn_check_conflicts'
// trigram + identifier scoring, real data) is always returned regardless
// of whether Gemini succeeds; the model is only asked to write a short
// Arabic note per match and an overall recommendation, and is explicitly
// told to use nothing but the match records given -- no outside
// knowledge about the named people, no invented facts. If there are no
// matches, the model is never called (same "zero matches -> no model
// call" rule as litigation-research/litigation-summarize). If
// GEMINI_API_KEY is missing or the call fails, that's a soft failure --
// unlike litigation-hearing-briefing/-summarize, the real safety-relevant
// output here is the raw match list, not AI prose, so a missing/failed
// AI step degrades to "here are the matches, make your own call" rather
// than blocking the whole check.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash';

const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  required: ['overall_recommendation', 'notes'],
  properties: {
    overall_recommendation: { type: 'STRING', enum: ['no_conflict_evident', 'needs_review', 'likely_conflict'] },
    notes: { type: 'ARRAY', items: { type: 'STRING' }, description: 'One short Arabic sentence per match, noting why it might or might not be a real conflict based only on the role and score given.' },
  },
};

const SYSTEM_INSTRUCTION =
  'You triage conflict-of-interest name-match results for an Egyptian law firm, in Arabic. You are given ONLY the matched records below (name, role, matter label, similarity score, 0 to 1) -- use nothing else. Do NOT use any outside knowledge about these names, do NOT guess at case content, and do NOT invent additional matches beyond what is given. For each match, write one short sentence noting whether the role and score suggest a real conflict (e.g. the same name as a party or opposing counsel on another live matter is a strong signal; a low-score fuzzy match on a common name is weak). Then give one overall_recommendation: "no_conflict_evident" only if there truly are no matches worth a second look, "needs_review" if a human should look before proceeding, or "likely_conflict" if multiple strong/exact matches suggest proceeding without a waiver or ethical wall would be improper.';

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'upload_documents', 'conflict_check_run');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { names, matter_id } = body ?? {};
  if (!Array.isArray(names) || names.length === 0 || names.some((n) => typeof n !== 'string' || !n.trim())) {
    return badRequest('names must be a non-empty array of non-empty strings');
  }

  const { data, error } = await asUser.rpc('fn_check_conflicts', {
    p_names: names.map((n: string) => n.trim()),
    p_exclude_matter_id: matter_id || null,
  });
  if (error) return json({ error: error.message }, 500);

  const matches = (data ?? []) as {
    matter_id: string; matter_label: string; party_role: string; matched_name: string; identifier: string | null; score: number;
  }[];

  await logAction(asService, callerId, 'conflict_check_run', {
    targetTable: matter_id ? 'matters' : undefined,
    targetId: matter_id || undefined,
    after: { names_checked: names.length, match_count: matches.length },
  });

  if (matches.length === 0) {
    return json({ matches: [], overall_recommendation: 'no_conflict_evident', ai_notes: null });
  }

  if (!GEMINI_API_KEY) {
    return json({ matches, overall_recommendation: null, ai_notes: null, ai_error: 'GEMINI_API_KEY is not configured -- raw matches are shown below without AI triage' });
  }

  const context = `السجلات المطابقة:\n${JSON.stringify(matches.map((m) => ({ name: m.matched_name, role: m.party_role, matter: m.matter_label, score: Math.round(m.score * 100) / 100 })), null, 2)}`;

  try {
    const apiResp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${GEMINI_API_KEY}`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: SYSTEM_INSTRUCTION }] },
          contents: [{ role: 'user', parts: [{ text: context }] }],
          generationConfig: { temperature: 0, responseMimeType: 'application/json', responseSchema: RESPONSE_SCHEMA },
        }),
      },
    );
    if (!apiResp.ok) {
      const errText = await apiResp.text().catch(() => '');
      return json({ matches, overall_recommendation: null, ai_notes: null, ai_error: `Gemini API returned ${apiResp.status}: ${errText.slice(0, 300)}` });
    }
    const apiJson = await apiResp.json();
    const textPart = apiJson.candidates?.[0]?.content?.parts?.find((p: { text?: string }) => typeof p.text === 'string');
    if (!textPart) return json({ matches, overall_recommendation: null, ai_notes: null, ai_error: 'model did not return the expected JSON output' });
    const triage = JSON.parse(textPart.text);
    return json({ matches, overall_recommendation: triage.overall_recommendation ?? null, ai_notes: triage.notes ?? null });
  } catch (e) {
    return json({ matches, overall_recommendation: null, ai_notes: null, ai_error: e instanceof Error ? e.message : String(e) });
  }
});
