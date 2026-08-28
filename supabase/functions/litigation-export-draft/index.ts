// litigation-export-draft
//
// POST { draft_id } -> { url }
//
// Generates a real .docx (not PDF — the earlier back-office build's own
// architecture note is explicit: jsPDF does not perform Arabic letter
// shaping/bidi, so PDF output comes out disconnected and reversed; Word
// files let Word's own text-shaping engine handle Arabic correctly once a
// paragraph is marked bidirectional/RTL). Uses the `docx` npm package via
// Deno's npm: specifier support — no hand-rolled OOXML.
//
// Blocks export if this draft has any draft_citations still
// unverified/flagged (plan section 03: a flagged citation blocks export
// until a lawyer confirms it). In this build that check is almost always
// a no-op — litigation-draft deliberately produces zero real citations
// (no corpus exists yet, see its own header note) — but the gate is real
// and will matter once citation-bound drafts exist.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { Document, Packer, Paragraph, TextRun, AlignmentType, HeadingLevel } from 'npm:docx@9';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'export_matter', 'draft_exported');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { draft_id } = body ?? {};
  if (!draft_id) return badRequest('draft_id is required');

  const { data: draft, error: draftError } = await asService
    .from('drafts')
    .select('id, matter_id, doc_type, version, content_text')
    .eq('id', draft_id)
    .maybeSingle();
  if (draftError || !draft) return badRequest('draft not found');

  const { data: canAccess } = await asUser.rpc('can_access_matter', { p_matter_id: draft.matter_id });
  if (!canAccess) return badRequest('no access to this matter');

  const { count: unresolvedCount } = await asService
    .from('draft_citations')
    .select('id', { count: 'exact', head: true })
    .eq('draft_id', draft_id)
    .in('status', ['unverified', 'flagged']);
  if (unresolvedCount && unresolvedCount > 0) {
    return badRequest(`cannot export: ${unresolvedCount} citation(s) on this draft are still unverified or flagged — resolve them first`);
  }

  const lines = String(draft.content_text || '').split('\n');
  const paragraphs = lines.map((line, i) =>
    new Paragraph({
      bidirectional: true,
      alignment: AlignmentType.RIGHT,
      heading: i === 0 ? HeadingLevel.HEADING_1 : undefined,
      children: [new TextRun({ text: line, rightToLeft: true })],
    }),
  );

  const doc = new Document({
    sections: [{ properties: {}, children: paragraphs.length ? paragraphs : [new Paragraph({ children: [new TextRun('')] })] }],
  });

  const buffer = await Packer.toBuffer(doc);
  const storagePath = `${draft.matter_id}/${draft.id}-v${draft.version}.docx`;

  const { error: uploadError } = await asService.storage
    .from('matter-drafts')
    .upload(storagePath, buffer, {
      contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      upsert: true,
    });
  if (uploadError) return json({ error: `docx upload failed: ${uploadError.message}` }, 500);

  await asService.from('drafts').update({ bucket_id: 'matter-drafts', storage_path: storagePath, status: 'exported' }).eq('id', draft_id);

  const { data: signed, error: signError } = await asService.storage
    .from('matter-drafts')
    .createSignedUrl(storagePath, 300);
  if (signError || !signed?.signedUrl) return json({ error: signError?.message ?? 'signing failed' }, 500);

  await logAction(asService, callerId, 'draft_exported', {
    targetTable: 'drafts', targetId: draft_id, after: { storage_path: storagePath },
  });

  return json({ url: signed.signedUrl, storage_path: storagePath });
});
