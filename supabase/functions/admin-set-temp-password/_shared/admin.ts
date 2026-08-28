// Shared helpers for every admin-*/litigation-*/legal-* Edge Function in
// this project. This exact file is duplicated into each function's own
// _shared/admin.ts at deploy time (Supabase bundles each function
// independently) — this copy at supabase/functions/_shared/ is the
// canonical source; keep every per-function copy in sync with it.
//
// The pattern every function in this project follows:
//   1. Build a client scoped to the CALLER's own JWT (asUser) — used only to
//      verify who they are and to call has_capability()/can_access_matter(),
//      so those checks run under the caller's own auth.uid(), not a spoofed
//      one.
//   2. Build a service-role client (asService) — used for the actual
//      privileged write, and for the audit_log entry, since authenticated
//      is never granted INSERT on audit_log directly.
//   3. Check the capability. Deny -> write a success:false audit row anyway
//      and return 403. A denied attempt is as interesting as a granted one.
//   4. Write the audit_log entry for the privileged action itself.
//
// CORS: every function here is called cross-origin, directly from the
// browser (litigation-agent.html, hosted elsewhere) — Supabase does not
// add CORS headers to custom Edge Functions automatically the way it does
// for REST/Auth/Storage, so without this every call fails the browser's
// preflight OPTIONS request and surfaces only as "Failed to fetch" with
// no further detail anywhere. Every function must call corsPreflight(req)
// first, before anything else.
//
// Deno.serve() has no special case for a *thrown* Response — it collapses
// to a generic 500 with no body. Every helper here RETURNS a Response
// (never throws), and every caller must check with `instanceof Response`
// before destructuring:
//
//   const caller = await requireCaller(req);
//   if (caller instanceof Response) return caller;
//   const { callerId, asUser } = caller;

import { createClient, type SupabaseClient } from 'jsr:@supabase/supabase-js@2';

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
const ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!;

export const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
};

export function corsPreflight(req: Request): Response | null {
  return req.method === 'OPTIONS' ? new Response(null, { status: 204, headers: CORS_HEADERS }) : null;
}

export function serviceClient(): SupabaseClient {
  return createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
}

export function userClient(authHeader: string): SupabaseClient {
  return createClient(SUPABASE_URL, ANON_KEY, {
    global: { headers: { Authorization: authHeader } },
    auth: { autoRefreshToken: false, persistSession: false },
  });
}

export class AdminError extends Response {
  constructor(status: number, message: string) {
    super(JSON.stringify({ error: message }), {
      status,
      headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
    });
  }
}

export async function requireCaller(req: Request): Promise<{ callerId: string; asUser: SupabaseClient } | Response> {
  const authHeader = req.headers.get('Authorization');
  if (!authHeader) return new AdminError(401, 'missing Authorization header');

  const asUser = userClient(authHeader);
  const { data, error } = await asUser.auth.getUser();
  if (error || !data.user) return new AdminError(401, 'invalid or expired session');

  return { callerId: data.user.id, asUser };
}

export async function requireCapability(
  asUser: SupabaseClient,
  asService: SupabaseClient,
  callerId: string,
  capability: string,
  action: string,
): Promise<Response | null> {
  const { data, error } = await asUser.rpc('has_capability', { cap: capability });
  if (error) return new AdminError(500, `capability check failed: ${error.message}`);

  if (!data) {
    await asService.from('audit_log').insert({
      actor_id: callerId,
      action,
      success: false,
      reason: `denied: missing capability ${capability}`,
    });
    return new AdminError(403, `missing capability: ${capability}`);
  }
  return null;
}

export async function logAction(
  asService: SupabaseClient,
  actorId: string,
  action: string,
  opts: {
    targetTable?: string;
    targetId?: string;
    before?: unknown;
    after?: unknown;
    reason?: string;
    success?: boolean;
  } = {},
) {
  await asService.from('audit_log').insert({
    actor_id: actorId,
    action,
    target_table: opts.targetTable ?? null,
    target_id: opts.targetId ?? null,
    before: opts.before ?? null,
    after: opts.after ?? null,
    reason: opts.reason ?? null,
    success: opts.success ?? true,
  });
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
  });
}

export function badRequest(message: string): Response {
  return new AdminError(400, message);
}
