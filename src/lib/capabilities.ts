import { createClient } from '@/lib/supabase/server';

export type Viewer = {
  userId: string;
  email: string;
  roles: string[];
  capabilities: Set<string>;
};

// Reflects what has_capability() would say for the current user, computed
// from the same two tables it reads (user_roles, role_capabilities) — both
// globally readable to any authenticated user per Phase A's RLS policies,
// so this is a cheap client-side-equivalent join, not a privileged query.
// This drives nav visibility only; every actual write still goes through
// an Edge Function's own requireCapability(), which is the real check.
export async function getViewer(): Promise<Viewer | null> {
  const supabase = await createClient();
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) return null;

  const { data: roleRows } = await supabase
    .from('user_roles')
    .select('role_id')
    .eq('user_id', user.id)
    .is('revoked_at', null);
  const roles = (roleRows ?? []).map((r) => r.role_id as string);

  if (roles.length === 0) {
    return { userId: user.id, email: user.email ?? '', roles: [], capabilities: new Set() };
  }

  const { data: capRows } = await supabase
    .from('role_capabilities')
    .select('capability_id')
    .in('role_id', roles);

  return {
    userId: user.id,
    email: user.email ?? '',
    roles,
    capabilities: new Set((capRows ?? []).map((c) => c.capability_id as string)),
  };
}
