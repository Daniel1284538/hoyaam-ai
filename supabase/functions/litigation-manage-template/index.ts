// litigation-manage-template
//
// POST { action: 'create', title, doc_type, content_text }
// POST { action: 'deactivate', template_id }
//
// Templates are the firm's own precedent documents (plan section 01:
// "Drafting from your templates"). Only content_text is handled here —
// this is a plain-text template editor, not a .docx-upload-and-parse
// pipeline (extracting text from an uploaded .docx server-side is real
// work this scaffold doesn't attempt; paste the template's text in
// directly for now).

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

  const capError = await requireCapability(asUser, asService, callerId, 'manage_templates', 'template_managed');
  if (capError) return capError;

  const body = await req.json().catch(() => null);
  const { action } = body ?? {};

  if (action === 'create') {
    const { title, doc_type, content_text } = body;
    if (!title || !doc_type || !content_text || !String(content_text).trim()) {
      return badRequest('title, doc_type and content_text are required');
    }
    const { data: tpl, error } = await asService
      .from('templates')
      .insert({ title, doc_type, content_text, created_by: callerId })
      .select('id')
      .single();
    if (error) return json({ error: error.message }, 409);

    await logAction(asService, callerId, 'template_created', { targetTable: 'templates', targetId: tpl.id, after: { title, doc_type } });
    return json({ template_id: tpl.id });
  }

  if (action === 'deactivate') {
    const { template_id } = body;
    if (!template_id) return badRequest('template_id is required');
    const { error } = await asService.from('templates').update({ active: false }).eq('id', template_id);
    if (error) return json({ error: error.message }, 500);

    await logAction(asService, callerId, 'template_deactivated', { targetTable: 'templates', targetId: template_id });
    return json({ template_id, active: false });
  }

  return badRequest("action must be 'create' or 'deactivate'");
});
