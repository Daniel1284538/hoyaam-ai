import { createClient } from '@/lib/supabase/server';
import { Card, Badge } from '@/components/ui';
import { GrantMatterAccessForm } from './grant-matter-access-form';

type MatterAccessRow = {
  id: string;
  matter_id: string;
  user_id: string;
  reason: string;
  granted_at: string;
  expires_at: string | null;
  revoked_at: string | null;
};

export default async function MatterAccessPage() {
  const supabase = await createClient();
  const { data: rows } = await supabase
    .from('matter_access')
    .select('id, matter_id, user_id, reason, granted_at, expires_at, revoked_at')
    .order('granted_at', { ascending: false });

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Matter access</h1>
        <p className="mt-1 text-sm text-ink-2">Per-matter access grants.</p>
      </div>

      <Card title="Grants">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-rule text-xs uppercase tracking-wide text-ink-3">
                <th className="py-2 pr-4 font-medium">Matter ID</th>
                <th className="py-2 pr-4 font-medium">User ID</th>
                <th className="py-2 pr-4 font-medium">Reason</th>
                <th className="py-2 pr-4 font-medium">Granted</th>
                <th className="py-2 pr-4 font-medium">Expires</th>
                <th className="py-2 pr-4 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {((rows ?? []) as MatterAccessRow[]).map((r) => (
                <tr key={r.id} className="border-b border-rule last:border-0">
                  <td className="py-2 pr-4 font-mono text-xs text-ink-2">{r.matter_id}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-2">{r.user_id}</td>
                  <td className="py-2 pr-4 text-ink">{r.reason}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-3">{new Date(r.granted_at).toLocaleString()}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-3">
                    {r.expires_at ? new Date(r.expires_at).toLocaleString() : '—'}
                  </td>
                  <td className="py-2 pr-4">
                    {r.revoked_at ? <Badge tone="risk">revoked</Badge> : <Badge tone="seal">active</Badge>}
                  </td>
                </tr>
              ))}
              {(rows ?? []).length === 0 && (
                <tr>
                  <td colSpan={6} className="py-4 text-center text-ink-3">
                    No matter-access grants yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <GrantMatterAccessForm />
    </div>
  );
}
