import { createClient } from '@/lib/supabase/server';
import { Card, Badge } from '@/components/ui';
import { InviteUserForm } from './invite-user-form';
import { SetRoleForm } from './set-role-form';
import { RevokeSessionsForm } from './revoke-sessions-form';
import { OffboardUserForm } from './offboard-user-form';

export type Role = { id: string; label_en: string };

type Profile = {
  id: string;
  full_name: string;
  bar_number: string | null;
  status: string;
  supervisor_id: string | null;
};

type UserRoleRow = {
  user_id: string;
  role_id: string;
};

export default async function UsersPage() {
  const supabase = await createClient();

  const [{ data: profiles }, { data: userRoles }, { data: roles }] = await Promise.all([
    supabase.from('profiles').select('id, full_name, bar_number, status, supervisor_id').order('full_name'),
    supabase.from('user_roles').select('user_id, role_id').is('revoked_at', null),
    supabase.from('roles').select('id, label_en').order('sort_order'),
  ]);

  const roleLabel = new Map((roles ?? []).map((r) => [r.id, r.label_en]));
  const rolesByUser = new Map<string, string[]>();
  for (const row of (userRoles ?? []) as UserRoleRow[]) {
    const list = rolesByUser.get(row.user_id) ?? [];
    list.push(roleLabel.get(row.role_id) ?? row.role_id);
    rolesByUser.set(row.user_id, list);
  }

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Users &amp; roles</h1>
        <p className="mt-1 text-sm text-ink-2">
          Everyone with a profile, their active role(s), and status. Email
          isn&apos;t stored on <code className="font-mono text-xs">profiles</code> and
          auth.users isn&apos;t queryable from here, so only what the profile
          holds is shown below.
        </p>
      </div>

      <Card title="Users">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-rule text-xs uppercase tracking-wide text-ink-3">
                <th className="py-2 pr-4 font-medium">Name</th>
                <th className="py-2 pr-4 font-medium">Bar #</th>
                <th className="py-2 pr-4 font-medium">Status</th>
                <th className="py-2 pr-4 font-medium">Role(s)</th>
                <th className="py-2 pr-4 font-medium">Supervisor</th>
                <th className="py-2 pr-4 font-medium">User ID</th>
              </tr>
            </thead>
            <tbody>
              {(profiles ?? []).map((p: Profile) => (
                <tr key={p.id} className="border-b border-rule last:border-0">
                  <td className="py-2 pr-4 text-ink">{p.full_name}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-2">{p.bar_number ?? '—'}</td>
                  <td className="py-2 pr-4">
                    <Badge tone={p.status === 'active' ? 'seal' : 'default'}>{p.status}</Badge>
                  </td>
                  <td className="py-2 pr-4">
                    {(rolesByUser.get(p.id) ?? []).length > 0
                      ? rolesByUser.get(p.id)!.join(', ')
                      : <span className="text-ink-3">no role</span>}
                  </td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-2">{p.supervisor_id ?? '—'}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-3">{p.id}</td>
                </tr>
              ))}
              {(profiles ?? []).length === 0 && (
                <tr>
                  <td colSpan={6} className="py-4 text-center text-ink-3">
                    No users yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <InviteUserForm roles={roles ?? []} />
      <SetRoleForm roles={roles ?? []} />
      <RevokeSessionsForm />
      <OffboardUserForm />
    </div>
  );
}
