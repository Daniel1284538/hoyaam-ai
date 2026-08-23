import { createClient } from '@/lib/supabase/client';

// Every privileged write goes through an admin-* Edge Function, never a
// direct table write from the client — see _shared/admin.ts in the
// handover: requireCaller/requireCapability run server-side against the
// caller's own JWT, which functions.invoke() attaches automatically.
export async function callEdgeFunction<T = unknown>(name: string, body: Record<string, unknown>): Promise<T> {
  const supabase = createClient();
  const { data, error } = await supabase.functions.invoke(name, { body });
  if (error) {
    // FunctionsHttpError carries the actual response body (our
    // { error: string } shape) on error.context — surface that message
    // rather than the generic "Edge Function returned a non-2xx status".
    const context = (error as { context?: Response }).context;
    if (context) {
      try {
        const parsed = await context.clone().json();
        if (parsed?.error) throw new Error(parsed.error);
      } catch {
        // fall through to the generic message below
      }
    }
    throw new Error(error.message);
  }
  return data as T;
}
